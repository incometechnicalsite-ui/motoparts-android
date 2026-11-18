-- MotoParts USA: Supabase setup. Run this ONCE in Supabase > SQL Editor > New query > Run.
-- It creates tables, security rules (RLS), order functions, and loads brands + categories only.
-- No products, sellers, orders, prices or exchange rates are invented.

create extension if not exists pg_trgm;

create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text,
  role text not null default 'customer' check (role in ('customer','seller','admin')),
  created_at timestamptz not null default now()
);

create or replace function handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into profiles (id, full_name) values (new.id, coalesce(new.raw_user_meta_data->>'full_name',''));
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function handle_new_user();

create table brands (
  id serial primary key,
  name text not null unique,
  slug text not null unique,
  logo_url text,
  is_active boolean not null default true
);

create table categories (
  id serial primary key,
  name text not null,
  slug text not null unique,
  group_name text not null,
  is_active boolean not null default true,
  unique (group_name, name)
);

create table bike_models (
  id serial primary key,
  brand_id int not null references brands(id) on delete cascade,
  name text not null,
  year_from int not null check (year_from >= 1900),
  year_to int check (year_to is null or year_to >= year_from),
  unique (brand_id, name, year_from)
);

create table products (
  id uuid primary key default gen_random_uuid(),
  category_id int not null references categories(id),
  name text not null,
  sku text not null unique,
  part_brand text,
  condition text not null check (condition in ('genuine','aftermarket')),
  description text,
  price_usd numeric(12,2) check (price_usd >= 0),
  price_bdt numeric(14,2) check (price_bdt >= 0),
  price_is_estimated boolean not null default true,
  stock int not null default 0 check (stock >= 0),
  shipping_info text,
  seller_name text not null default 'MotoParts USA',
  status text not null default 'draft' check (status in ('draft','published','unavailable')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index products_name_trgm on products using gin (name gin_trgm_ops);

create table product_images (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id) on delete cascade,
  url text not null unique,           -- the same photo URL cannot be reused on another product
  sort_order int not null default 0,
  created_at timestamptz not null default now()
);

create table product_compatibility (
  product_id uuid not null references products(id) on delete cascade,
  bike_model_id int not null references bike_models(id) on delete cascade,
  primary key (product_id, bike_model_id)
);

-- A product can only be published with at least one image and a price
create or replace function enforce_publish_rules() returns trigger language plpgsql as $$
begin
  if new.status = 'published' then
    if not exists (select 1 from product_images where product_id = new.id) then
      raise exception 'Cannot publish product without at least one image';
    end if;
    if new.price_usd is null then
      raise exception 'Cannot publish product without a price';
    end if;
  end if;
  new.updated_at = now();
  return new;
end $$;
create trigger products_publish_rules before insert or update on products
  for each row execute function enforce_publish_rules();

create table addresses (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  full_name text not null,
  phone text not null,
  line1 text not null,
  line2 text,
  city text not null,
  state text,
  postal_code text,
  country text not null check (char_length(country) = 2),
  created_at timestamptz not null default now()
);

-- Admin sets the shipping fee per country. No row = we do not ship there.
create table shipping_zones (
  country text primary key check (char_length(country) = 2),
  fee_usd numeric(10,2) not null check (fee_usd >= 0)
);

-- Admin sets key 'usd_to_bdt'. Not seeded.
create table settings (
  key text primary key,
  value text not null,
  updated_at timestamptz not null default now()
);

create table orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  address_snapshot jsonb not null,
  currency text not null check (currency in ('USD','BDT')),
  exchange_rate numeric(14,6),
  subtotal_usd numeric(12,2) not null,
  shipping_usd numeric(12,2) not null,
  total_usd numeric(12,2) not null,
  status text not null default 'pending'
    check (status in ('pending','confirmed','processing','shipped','delivered','cancelled','refund_requested','refunded')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references orders(id) on delete cascade,
  product_id uuid not null references products(id),
  product_name text not null,
  sku text not null,
  quantity int not null check (quantity > 0),
  unit_price_usd numeric(12,2) not null,
  status text not null default 'pending'
);

-- ===== Security (Row Level Security) =====
alter table profiles enable row level security;
alter table brands enable row level security;
alter table categories enable row level security;
alter table bike_models enable row level security;
alter table products enable row level security;
alter table product_images enable row level security;
alter table product_compatibility enable row level security;
alter table addresses enable row level security;
alter table shipping_zones enable row level security;
alter table settings enable row level security;
alter table orders enable row level security;
alter table order_items enable row level security;

create policy "read active brands" on brands for select using (is_active);
create policy "read active categories" on categories for select using (is_active);
create policy "read bike models" on bike_models for select using (true);
create policy "read published products" on products for select using (status = 'published');
create policy "read images of published products" on product_images for select
  using (exists (select 1 from products p where p.id = product_id and p.status = 'published'));
create policy "read compatibility of published products" on product_compatibility for select
  using (exists (select 1 from products p where p.id = product_id and p.status = 'published'));
create policy "read shipping zones" on shipping_zones for select using (true);
create policy "read exchange rate only" on settings for select using (key = 'usd_to_bdt');
create policy "own profile" on profiles for select using (id = auth.uid());
create policy "own addresses" on addresses for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own orders" on orders for select using (user_id = auth.uid());
create policy "own order items" on order_items for select
  using (exists (select 1 from orders o where o.id = order_id and o.user_id = auth.uid()));

grant select on brands, categories, bike_models, products, product_images, product_compatibility,
  shipping_zones, settings to anon, authenticated;
grant select on profiles, orders, order_items to authenticated;
grant select, insert, update, delete on addresses to authenticated;

-- ===== Order functions (the only way to create or cancel an order) =====
create or replace function create_order(p_address_id uuid, p_currency text, p_items jsonb)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_addr addresses%rowtype;
  v_fee numeric;
  v_rate numeric := null;
  v_sub numeric := 0;
  v_order uuid;
  it jsonb;
  p products%rowtype;
  q int;
begin
  if v_uid is null then raise exception 'Please log in first'; end if;
  if p_currency not in ('USD','BDT') then raise exception 'Invalid currency'; end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'Cart is empty';
  end if;
  select * into v_addr from addresses where id = p_address_id and user_id = v_uid;
  if not found then raise exception 'Address not found'; end if;
  select fee_usd into v_fee from shipping_zones where country = upper(v_addr.country);
  if v_fee is null then raise exception 'We do not ship to % yet', v_addr.country; end if;
  if p_currency = 'BDT' then
    select value::numeric into v_rate from settings where key = 'usd_to_bdt';
    if v_rate is null then raise exception 'BDT checkout is not available yet: exchange rate not set'; end if;
  end if;

  insert into orders (user_id, address_snapshot, currency, exchange_rate, subtotal_usd, shipping_usd, total_usd)
  values (v_uid, to_jsonb(v_addr) - 'user_id', p_currency, v_rate, 0, v_fee, 0)
  returning id into v_order;

  for it in select * from jsonb_array_elements(p_items) loop
    q := (it->>'quantity')::int;
    if q is null or q < 1 or q > 100 then raise exception 'Invalid quantity'; end if;
    select * into p from products where id = (it->>'product_id')::uuid and status = 'published' for update;
    if not found then raise exception 'A product is no longer available'; end if;
    if p.stock < q then raise exception 'Not enough stock for %', p.name; end if;
    update products set stock = stock - q where id = p.id;
    insert into order_items (order_id, product_id, product_name, sku, quantity, unit_price_usd)
    values (v_order, p.id, p.name, p.sku, q, p.price_usd);
    v_sub := v_sub + p.price_usd * q;
  end loop;

  update orders set subtotal_usd = round(v_sub, 2), total_usd = round(v_sub + v_fee, 2) where id = v_order;
  return v_order;
end $$;

create or replace function cancel_order(p_order_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare o orders%rowtype; r record;
begin
  select * into o from orders where id = p_order_id and user_id = auth.uid() for update;
  if not found then raise exception 'Order not found'; end if;
  if o.status not in ('pending','confirmed') then raise exception 'Order can no longer be cancelled'; end if;
  for r in select product_id, quantity from order_items where order_id = o.id loop
    update products set stock = stock + r.quantity where id = r.product_id;
  end loop;
  update orders set status = 'cancelled', updated_at = now() where id = o.id;
  update order_items set status = 'cancelled' where order_id = o.id;
end $$;

revoke execute on function create_order(uuid, text, jsonb) from public, anon;
revoke execute on function cancel_order(uuid) from public, anon;
grant execute on function create_order(uuid, text, jsonb) to authenticated;
grant execute on function cancel_order(uuid) to authenticated;

-- ===== Reference data (brands and categories only) =====
INSERT INTO brands (name, slug) VALUES
 ('Honda','honda'),('Yamaha','yamaha'),('Suzuki','suzuki'),('Kawasaki','kawasaki'),
 ('Bajaj','bajaj'),('TVS','tvs'),('Hero','hero'),('KTM','ktm'),
 ('BMW Motorrad','bmw-motorrad'),('Royal Enfield','royal-enfield'),('Ducati','ducati'),
 ('Harley-Davidson','harley-davidson'),('Triumph','triumph'),('Aprilia','aprilia'),
 ('Benelli','benelli'),('CFMOTO','cfmoto'),('Lifan','lifan'),('Keeway','keeway')
ON CONFLICT DO NOTHING;

INSERT INTO categories (group_name, name, slug) VALUES
 ('Engine','Engine Parts','engine-parts'),('Engine','Piston','piston'),('Engine','Cylinder','cylinder'),
 ('Engine','Crankshaft','crankshaft'),('Engine','Valves','valves'),('Engine','Engine Bearings','engine-bearings'),('Engine','Gaskets','gaskets'),
 ('Braking','Brake Pads','brake-pads'),('Braking','Brake Disc','brake-disc'),('Braking','Brake Caliper','brake-caliper'),
 ('Braking','Brake Shoes','brake-shoes'),('Braking','Brake Master Cylinder','brake-master-cylinder'),
 ('Transmission','Clutch Plates','clutch-plates'),('Transmission','Clutch Cable','clutch-cable'),('Transmission','Gearbox Parts','gearbox-parts'),
 ('Transmission','Chain Kits','chain-kits'),('Transmission','Sprockets','sprockets'),
 ('Electrical','Battery','battery'),('Electrical','Spark Plugs','spark-plugs'),('Electrical','Ignition Coil','ignition-coil'),
 ('Electrical','Starter Motor','starter-motor'),('Electrical','Headlights','headlights'),('Electrical','Indicators','indicators'),('Electrical','Wiring','wiring'),
 ('Maintenance','Engine Oil','engine-oil'),('Maintenance','Air Filters','air-filters'),('Maintenance','Oil Filters','oil-filters'),
 ('Maintenance','Coolant','coolant'),('Maintenance','Lubricants','lubricants'),
 ('Other Parts','Tyres','tyres'),('Other Parts','Wheels','wheels'),('Other Parts','Suspension','suspension'),
 ('Other Parts','Shock Absorbers','shock-absorbers'),('Other Parts','Mirrors','mirrors'),('Other Parts','Levers','levers'),
 ('Other Parts','Cables','cables'),('Other Parts','Body Parts','body-parts'),('Other Parts','Exhaust Systems','exhaust-systems'),
 ('Other Parts','Bearings','bearings'),('Other Parts','Seals','seals'),('Other Parts','Cooling Systems','cooling-systems'),
 ('Accessories','Helmets','helmets'),('Accessories','Gloves','gloves'),('Accessories','Riding Jackets','riding-jackets'),
 ('Accessories','Riding Boots','riding-boots'),('Accessories','Mobile Holders','mobile-holders'),
 ('Accessories','Workshop Tools','workshop-tools'),('Accessories','Motorcycle Accessories','motorcycle-accessories')
ON CONFLICT DO NOTHING;
