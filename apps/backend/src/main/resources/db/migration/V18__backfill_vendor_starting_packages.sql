-- The onboarding form historically stored the starting package on the vendor row,
-- while the vendor workspace reads packages from vendor_packages. Backfill the
-- missing collection entries so existing vendors see their saved starting package.
insert into vendor_packages (vendor_id, service_type, service_id, package_name, description, price, created_at)
select
    vendor.id,
    coalesce((
        select case
            when upper(category.name) like '%PHOTO%' then 'PHOTOGRAPHY'
            when upper(category.name) like '%MAKEUP%' or upper(category.name) like '%MEHENDI%' then 'MAKEUP'
            when upper(category.name) like '%DJ%' or upper(category.name) like '%MUSIC%' then 'DJ'
            when upper(category.name) like '%DECOR%' then 'DECORATION'
            else 'CATERING'
        end
        from vendor_category_mapping mapping
        join vendor_categories category on category.id = mapping.category_id
        where mapping.vendor_id = vendor.id
        order by category.id
        limit 1
    ), 'CATERING'),
    vendor.id,
    vendor.package_name,
    vendor.package_description,
    vendor.starting_price,
    coalesce(vendor.updated_at, vendor.created_at, now())
from vendors vendor
where nullif(trim(vendor.package_name), '') is not null
  and vendor.starting_price is not null
  and vendor.starting_price > 0
  and not exists (
      select 1
      from vendor_packages package_item
      where package_item.vendor_id = vendor.id
  );
