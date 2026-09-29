"""Инструменты агента поверх каталога.

Важное здесь не код, а то, что агенту НЕ дано: сменить цену, удалить товар,
выполнить произвольный запрос. Набор инструментов — это и есть граница его
полномочий, и сужать её надёжнее, чем просить в подсказке «ничего не ломай».
Скидку, например, агент придумает охотно, а отвечать за неё будет магазин —
поэтому инструмента на цену здесь нет.
"""
from agent import Tool


def build(products):
    """products — список словарей с title, price, available; тот же, что в shop."""

    def find(args):
        query = str(args.get("query", "")).strip().lower()
        if not query:
            return "нужен аргумент query"
        found = [p for p in products if query in p["title"].lower()]
        if not found:
            return "ничего не найдено по запросу " + query
        return "\n".join("%s | %d руб | доступно %d"
                         % (p["title"], p["price"], p["available"]) for p in found)

    def stock(args):
        title = str(args.get("title", "")).strip().lower()
        for p in products:
            if p["title"].lower() == title:
                return "%s: доступно %d" % (p["title"], p["available"])
        return "товара с таким названием нет"

    def reserve(args):
        title = str(args.get("title", "")).strip().lower()
        quantity = int(args.get("quantity", 1))
        for p in products:
            if p["title"].lower() == title:
                if quantity > p["available"]:
                    return "столько нет в наличии"
                p["available"] -= quantity
                return "зарезервировано, осталось доступно %d" % p["available"]
        return "товара с таким названием нет"

    return [
        Tool("find_products", 'найти товары по части названия; аргументы: {"query": "мышь"}', find),
        Tool("stock", 'остаток товара по названию; аргументы: {"title": "Беспроводная мышь"}', stock),
        Tool("reserve",
             'зарезервировать товар; аргументы: {"title": "Беспроводная мышь", "quantity": 1}',
             reserve, mutating=True),
    ]
