-- Сто тысяч товаров: столько, чтобы разница между планами запроса стала видна глазом.
insert into products (id, title, price, stock, reserved, version)
select
    gen_random_uuid(),
    (array['Мышь','Клавиатура','Монитор','Наушники','Кабель','Хаб','Коврик','Веб-камера','Микрофон','Подставка'])[1 + (i % 10)]
        || ' ' ||
    (array['Logitech','Keychron','Dell','Sony','Baseus','Anker','Razer','HyperX','Xiaomi','Ugreen'])[1 + ((i / 10) % 10)]
        || ' модель ' || i,
    (100 + (i % 90000))::numeric / 10,
    (i % 50),
    0,
    0
from generate_series(1, 100000) as s(i);

analyze products;
