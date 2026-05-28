#!/usr/bin/env python3
"""
商品FAQ知识库生成脚本 — 每商品~20条Q&A
支持MySQL直连或JSON数据文件两种模式
用法: python generate_faq.py [--from-json data.json]
"""
import json, os, sys, argparse
from collections import defaultdict

DB_CONFIG = {
    'host': 'localhost', 'port': 3306, 'user': 'root',
    'password': 'root', 'database': 'mall', 'charset': 'utf8mb4'
}

GENERIC_FAQ = [
    {"question":"支持哪些支付方式？","answer":"支持支付宝和微信支付两种方式。下单时选择对应的支付方式即可完成付款。","category":"支付"},
    {"question":"支付失败怎么办？","answer":"请确认：1）支付宝/微信账户余额充足；2）网络连接正常；3）未超出单笔支付限额。如仍失败，请联系人工客服处理。","category":"支付"},
    {"question":"可以货到付款吗？","answer":"目前暂不支持货到付款，请在下单时完成在线支付。","category":"支付"},
    {"question":"如何申请退换货？","answer":"在订单详情页点击'申请售后'，选择退货或换货，填写原因后提交。审核通过后按指引寄回商品即可。","category":"退换货"},
    {"question":"退货后多久能收到退款？","answer":"仓库收到退货并确认无误后，1-3个工作日内原路退回您的支付账户。","category":"退换货"},
    {"question":"退换货运费谁出？","answer":"商品质量问题由我们承担运费；非质量问题的退换货，需您承担寄回运费。","category":"退换货"},
    {"question":"发货需要多长时间？","answer":"正常情况下，下单后24小时内发货。大促期间可能延迟至48小时。您可在订单详情页查看物流信息。","category":"物流"},
    {"question":"如何查询物流信息？","answer":"在'我的订单'中点击对应订单，即可查看实时物流状态和快递单号。","category":"物流"},
    {"question":"保修多久？","answer":"手机/平板/笔记本类商品享受一年全国联保；家电类商品享受三年保修；服装鞋帽类商品享受7天无理由退换。具体以商品详情页说明为准。","category":"售后"},
    {"question":"怎么联系人工客服？","answer":"在工作时间(9:00-21:00)发送您的问题，如AI无法解答会自动转接人工客服。","category":"售后"},
    {"question":"如何取消订单？","answer":"未付款订单1小时后自动取消；已付款未发货订单可在订单详情页申请取消。","category":"订单"},
    {"question":"可以修改订单吗？","answer":"未发货的订单可修改收货地址；已发货的订单无法修改。","category":"订单"},
]

def fprice(p):
    """格式化价格"""
    if p is None: return "暂无"
    return f"¥{p}元"

def load_from_mysql():
    """从MySQL加载商品数据"""
    import pymysql
    conn = pymysql.connect(**DB_CONFIG)
    c = conn.cursor()
    c.execute("""SELECT id, name, brand_name, product_category_name, price,
        original_price, promotion_price, promotion_type, stock, sub_title,
        keywords, sale, gift_point, gift_growth, use_point_limit, service_ids
        FROM pms_product WHERE delete_status=0 AND publish_status=1""")
    cols = [d[0] for d in c.description]
    products = [dict(zip(cols, row)) for row in c.fetchall()]
    c.execute("""SELECT product_id, sku_code, price, stock, sp_data
        FROM pms_sku_stock""")
    cols2 = [d[0] for d in c.description]
    skus = defaultdict(list)
    for row in c.fetchall():
        d = dict(zip(cols2, row))
        skus[d['product_id']].append(d)
    c.close(); conn.close()
    return products, skus

def load_from_json(path):
    """从JSON文件加载商品数据（备选方案）"""
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    products = data.get('products', [])
    skus_raw = data.get('skus', {})
    skus = {int(k): v for k, v in skus_raw.items()}
    return products, skus

def generate_product_qa(p, skus_list, cat_products, all_products):
    """为一个商品生成~20条Q&A"""
    name = p['name']
    brand = p.get('brand_name', '') or ''
    cat = p.get('product_category_name', '') or ''
    price = p.get('price')
    original = p.get('original_price')
    promo_price = p.get('promotion_price')
    promo_type = p.get('promotion_type', 0)
    stock = p.get('stock', 0) or 0
    subtitle = (p.get('sub_title', '') or '')[:200]
    sale = p.get('sale', 0) or 0
    gift_point = p.get('gift_point', 0) or 0
    gift_growth = p.get('gift_growth', 0) or 0
    use_point_limit = p.get('use_point_limit', 0) or 0

    promo_map = {0:"暂无", 1:"打折促销", 2:"满减优惠", 3:"秒杀活动", 4:"限时优惠"}
    promo_text = promo_map.get(promo_type, "暂无")

    qas = []

    # 1. 价格与优惠 (4条)
    qas.append((f"{name}多少钱？", f"{name}当前售价{fprice(price)}。"
        + (f"原价{fprice(original)}，" if original and original != price else "")
        + (f"活动价{fprice(promo_price)}。" if promo_price else "。")))

    qas.append((f"{name}有优惠吗？",
        f"{name}当前{f'有{promo_text}活动' if promo_type and promo_type > 0 else '暂无额外优惠活动'}。"
        + f"售价{fprice(price)}。"
        + (f"原价{fprice(original)}，已降价。" if original and original != price else "")
        + ("建议关注商城首页获取最新优惠信息。" if not promo_type or promo_type == 0 else "")))

    qas.append((f"{name}性价比怎么样？",
        f"{name}售价{fprice(price)}，"
        + (f"相比原价{fprice(original)}有优惠。" if original and original > price else "价格稳定。")
        + (f"在同品类中属于{'高端' if price and cat_products and price >= sorted([x.get('price',0) or 0 for x in cat_products], reverse=True)[len(cat_products)//3] else '中端' if price else ''}价位。" if cat_products else "")
        + (f" 当前销量{sale}件，口碑良好。" if sale else "")))

    qas.append((f"{name}支持分期付款吗？",
        "部分商品支持花呗分期和信用卡分期，具体以结算页面显示为准。"))

    # 2. 库存与购买 (3条)
    qas.append((f"{name}有货吗？",
        f"{name}当前{'有货' if stock > 0 else '暂时缺货'}，库存{f'{stock}件' if stock > 0 else '请关注补货通知'}。"
        + ("库存充足，可放心购买。" if stock > 500 else "建议尽快下单，库存有限。" if 0 < stock <= 500 else "")))

    qas.append((f"{name}怎么购买？",
        f"在商品详情页选择规格后点击「加入购物车」或「立即购买」，进入结算页面完成支付即可。"))

    qas.append((f"{name}有赠品吗？",
        f"{name}当前"
        + (f"赠送{gift_point}积分、{gift_growth}成长值。" if gift_point or gift_growth else "暂无赠品活动，建议关注店铺首页。")
        + (f"可使用积分抵扣，{use_point_limit}积分起用。" if use_point_limit else "")))

    # 3. 适用人群 (2条)
    keywords_lower = (p.get('keywords', '') or '').lower()
    crowd = ""
    if '学生' in subtitle or '学生' in keywords_lower:
        crowd = "学生、年轻用户群体"
    elif '商务' in subtitle or '高端' in subtitle or '旗舰' in subtitle:
        crowd = "商务人士、追求高端品质的用户"
    elif '游戏' in subtitle or '电竞' in keywords_lower:
        crowd = "游戏玩家、对性能有高要求的用户"
    elif '拍照' in subtitle or '影像' in subtitle:
        crowd = "摄影爱好者、注重拍照的用户"
    elif '运动' in subtitle or '跑' in subtitle or '休闲' in subtitle:
        crowd = "运动爱好者、追求舒适穿着的用户"
    elif '基础' in subtitle or '简约' in subtitle:
        crowd = "日常穿搭、追求简约风格的用户"
    elif '大电量' in subtitle or '续航' in subtitle:
        crowd = "重度手机用户、对续航有要求的用户"
    else:
        crowd = f"对{brand}品牌和{cat}品类感兴趣的消费者"

    qas.append((f"{name}适合什么人？",
        f"{name}适合{crowd}。{subtitle[:150]}"))

    qas.append((f"{name}适合送人吗？",
        f"{name}{'适合' if price and price > 2000 else '作为实用礼物'}作为礼物。"
        + (f"品牌{brand}知名度高，送礼体面。" if brand in ('苹果','华为','NIKE','三星') else "")
        + (f"价格适中，自用送人都合适。" if price and price < 2000 else "")))

    # 4. 功能与特点 (3条)
    qas.append((f"{name}有什么特点？",
        f"{name}主要特点：{subtitle[:200] if subtitle else f'{brand}出品，品质保证。'}"
        + (f" 累计销量{sale}件。" if sale else "")))

    sq = subtitle.lower()
    features = []
    if '5g' in sq: features.append("支持5G网络")
    if '快充' in sq or '闪充' in sq: features.append("支持快充/闪充")
    if '2k' in sq or '超清' in sq or '4k' in sq or 'hdr' in sq.lower(): features.append("高清显示屏")
    if '高刷' in sq or '120hz' in sq: features.append("高刷新率屏幕")
    if 'ois' in sq: features.append("OIS光学防抖")
    if '双卡' in sq: features.append("双卡双待")
    if '防冻' in sq: features.append("防冻设计")
    if '变频' in sq: features.append("变频节能")
    if '气垫' in sq or 'air' in sq: features.append("气垫缓震科技")
    if 'ssd' in sq or '固态' in sq: features.append("高速固态存储")
    if features:
        qas.append((f"{name}配置怎么样？",
            f"{name}配置亮点：{'、'.join(features)}。{subtitle[:100]}"))

    qas.append((f"{name}质量怎么样？",
        f"{name}由{brand}出品，品质有保障。"
        + ("全国联保，售后无忧。" if cat in ('手机通讯','平板电脑','笔记本','电视','厨卫大电') else "")
        + (f"累计销量{sale}件，用户反馈良好。" if sale else "")))

    # 5. 品牌相关 (2条)
    qas.append((f"{brand}这个品牌怎么样？",
        f"{brand}是知名品牌，在{cat}领域有良好的口碑和用户基础。"
        + ("产品质量和售后服务都有保障。" if brand in ('苹果','华为','小米','三星','NIKE') else "")))

    qas.append((f"{name}是正品吗？",
        f"本商城所有商品均为{brand}官方正品，支持防伪查询，请放心购买。"))

    # 6. 对比 (2条)
    others = [x for x in (cat_products or []) if x.get('id') != p.get('id')]
    if len(others) >= 1:
        other = sorted(others, key=lambda x: abs((x.get('price') or 0) - (price or 0)))[0]
        other_name = other.get('name', '同类商品')
        other_price = other.get('price')
        qas.append((f"{name}和{other_name}哪个好？",
            f"{name} vs {other_name} 对比：\n"
            f"• 价格：{name} {fprice(price)} vs {other_name} {fprice(other_price)}\n"
            f"• 品牌：{brand} vs {other.get('brand_name','')}\n"
            f"• 库存：{stock}件 vs {other.get('stock',0)}件\n"
            + (f"• {name}特点：{subtitle[:100]}\n" if subtitle else "")
            + (f"• {other_name}特点：{(other.get('sub_title','') or '')[:100]}\n" if other.get('sub_title') else "")
            + f"\n总结：{'价格相当，各有特色' if price and other_price and abs(price-other_price) < 500 else f'{name if price and other_price and price > other_price else other_name}价格更高，{other_name if price and other_price and price > other_price else name}性价比更优'}。选择哪款取决于您的需求和预算。"))

    qas.append((f"同价位有哪些选择？",
        f"在{cat}品类中，与{name}价位（{fprice(price)}）相近的有：\n"
        + "\n".join(f"  • {x.get('name','')} - {fprice(x.get('price'))} ({x.get('brand_name','')})"
            for x in sorted(others, key=lambda x: abs((x.get('price') or 0) - (price or 0)))[:5])
        + ("\n\n建议根据品牌偏好和具体配置选择。" if others else "")))

    # 7. 规格与选配 (2条)
    if skus_list:
        sku_lines = []
        for sku in skus_list[:6]:
            sp = sku.get('sp_data', '') or ''
            sku_lines.append(f"  • {sku.get('sku_code','')} - {fprice(sku.get('price'))} - 库存{sku.get('stock',0)}件"
                + (f" ({sp})" if sp else ""))
        qas.append((f"{name}有什么规格可选？",
            f"{name}有以下{len(skus_list)}种规格：\n" + "\n".join(sku_lines)
            + (f"\n...共{len(skus_list)}种规格" if len(skus_list) > 6 else "")))
        qas.append((f"{name}怎么选规格？",
            f"选择规格时建议考虑：1）预算（价格从低到高）；2）使用需求（如手机容量、鞋码大小等）；3）库存情况。具体可参考商品详情页的规格说明。"))
    else:
        qas.append((f"{name}有什么规格可选？", f"{name}为单一规格商品，{fprice(price)}，库存{stock}件。"))
        qas.append((f"{name}怎么选？", f"{name}是标准规格，直接下单即可。如需了解更多信息，可查看商品详情页。"))

    # 8. 售后与物流 (2条)
    warranty_map = {"手机通讯":"一年全国联保","平板电脑":"一年全国联保","笔记本":"一年全国联保",
        "电视":"一年全国联保","厨卫大电":"三年保修","硬盘":"三年保修"}
    warranty = warranty_map.get(cat, "7天无理由退换")
    qas.append((f"{name}保修多久？", f"{name}享受{warranty}。如遇质量问题，请在收到货后及时联系客服处理。"))

    qas.append((f"{name}什么时候发货？", f"下单后24小时内发货，一般3-5个工作日送达。大促期间可能延迟至48小时。"))

    return [(q, a, cat) for q, a in qas]


def generate(products, skus):
    """主生成逻辑"""
    by_cat = defaultdict(list)
    for p in products:
        by_cat[p.get('product_category_name', '') or '其他'].append(p)

    product_faq = []

    # 品类级FAQ
    for cat, prods in sorted(by_cat.items()):
        brands = sorted(set(p.get('brand_name','') for p in prods if p.get('brand_name')))
        price_min = min(p.get('price') or 0 for p in prods)
        price_max = max(p.get('price') or 0 for p in prods)

        product_faq.append((f"{cat}有哪些推荐？",
            f"{cat}共有{len(prods)}款商品，覆盖{len(brands)}个品牌：" + "、".join(brands) +
            f"。价格区间{fprice(price_min)}-{fprice(price_max)}。",
            cat))

        product_faq.append((f"{cat}大概多少钱？",
            f"{cat}价格区间为{fprice(price_min)}至{fprice(price_max)}。具体价格因品牌和配置而异。",
            cat))

        # 品类对比表（≥2款）
        if len(prods) >= 2:
            lines = [f"{cat}型号对比：\n| 型号 | 品牌 | 价格 | 库存 | 特点 |",
                "|------|------|------|------|------|"]
            for p in sorted(prods, key=lambda x: x.get('price') or 0, reverse=True):
                feat = (p.get('sub_title','') or '')[:80]
                lines.append(f"| {p.get('name','')} | {p.get('brand_name','')} | {fprice(p.get('price'))} | {p.get('stock',0) or 0}件 | {feat} |")
            product_faq.append((f"{cat}型号对比", "\n".join(lines), cat))

    # 每商品~20条Q&A
    for p in products:
        pid = p.get('id')
        sku_list = skus.get(pid, [])
        cat_prods = by_cat.get(p.get('product_category_name','') or '其他', [])
        qas = generate_product_qa(p, sku_list, cat_prods, products)
        for q, a, c in qas:
            product_faq.append((q, a, c))

    # 品牌FAQ
    brand_prods = defaultdict(list)
    for p in products:
        if p.get('brand_name'):
            brand_prods[p.get('brand_name')].append(p)
    for brand, prods in sorted(brand_prods.items()):
        lines = [f"{brand}品牌共有{len(prods)}款在售商品："]
        for p in prods:
            lines.append(f"  • {p.get('name','')} - {fprice(p.get('price'))} ({p.get('product_category_name','')})")
        product_faq.append((f"{brand}有哪些产品？", "\n".join(lines), "品牌"))

    return product_faq


def main():
    parser = argparse.ArgumentParser(description='商品FAQ生成脚本')
    parser.add_argument('--from-json', help='从JSON文件读取商品数据（备选方案）')
    args = parser.parse_args()

    print("=" * 50)
    print("商品FAQ知识库生成脚本")
    print("=" * 50)

    # 1. 加载数据
    print("\n[1/3] 加载商品数据...")
    if args.from_json:
        products, skus = load_from_json(args.from_json)
    else:
        try:
            products, skus = load_from_mysql()
        except Exception as e:
            print(f"  MySQL连接失败: {e}")
            print("  请使用 --from-json data.json 从JSON文件加载")
            sys.exit(1)
    print(f"  商品: {len(products)} 款, SKU: {sum(len(v) for v in skus.values())} 个")

    # 2. 生成FAQ
    print("[2/3] 生成Q&A...")
    product_faq = generate(products, skus)
    all_faq = GENERIC_FAQ + [{"question": q, "answer": a, "category": c} for q, a, c in product_faq]
    print(f"  通用FAQ: {len(GENERIC_FAQ)} 条")
    print(f"  商品FAQ: {len(product_faq)} 条")
    print(f"  合计: {len(all_faq)} 条")

    # 3. 输出
    output_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'faq')
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, 'product_faq.json')
    print(f"\n[3/3] 写入 {output_path}...")
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(all_faq, f, ensure_ascii=False, indent=2)
    print(f"  完成！")

    # 统计
    cat_counts = defaultdict(int)
    for item in product_faq:
        cat_counts[item[2]] += 1
    print("\n各品类FAQ数量：")
    for cat, count in sorted(cat_counts.items(), key=lambda x: -x[1]):
        print(f"  {cat}: {count}条")
    per_product = len(product_faq) / len(products) if products else 0
    print(f"\n每商品平均: {per_product:.1f}条FAQ")


if __name__ == '__main__':
    main()
