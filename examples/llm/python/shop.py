"""Данные магазина: правила из общих документов и карточки товаров.

Товары попадают в базу знаний намеренно. Половина вопросов покупателя — не про
правила, а про ассортимент («есть ли беспроводная мышь до двух тысяч»), и
отвечать на них по общим документам нечем.
"""
import pathlib

DOCS = pathlib.Path(__file__).resolve().parent.parent / "knowledge"

PRODUCTS = [
    {"title": "Беспроводная мышь", "price": 1990, "available": 5},
    {"title": "Мышь игровая премиум", "price": 7900, "available": 3},
    {"title": "Мышь офисная", "price": 890, "available": 0},
    {"title": "Механическая клавиатура", "price": 5400, "available": 2},
]


def fill(base):
    """Собирает базу знаний заново: сначала правила, потом карточки товаров."""
    base.clear()
    for path in sorted(DOCS.glob("*.md")):
        base.index(path.name, path.read_text(encoding="utf-8"))
    for product in PRODUCTS:
        base.index("каталог: " + product["title"], card(product))
    return base


def card(product):
    """Карточка кладётся фразой, а не полями через запятую: вопрос задают
    словами, и искать придётся по словам."""
    stock = ("В наличии %d штук." % product["available"]) if product["available"] else "Нет в наличии."
    return "Товар «%s». Цена %d рублей. %s" % (product["title"], product["price"], stock)
