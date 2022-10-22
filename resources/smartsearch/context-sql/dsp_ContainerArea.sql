/* dsp_ContainerArea */
select cast(ca.id as varchar) as key,
    array[
        /* address.view */
        a.view_,
        /* lkCode */
        ca.lk_code
    ] as search_values,

    array[
        /* address.view */
        a.view_,
        /* lkCode */
        concat(ca.lk_code, ' (', a.view_, ')')
    ] as display_values,

    ca.delete_ts is not null or coalesce(ca.is_archived, false) as is_archive,

    array[
        (select concat('region.id=', string_agg(distinct cast(l.region_id as varchar), ','))
        from dsp_region_container_area_link as l
        where l.container_area_id = ca.id)
    ] as tags
from dsp_container_area as ca
    join dsp_address as a on a.id = ca.address_id
