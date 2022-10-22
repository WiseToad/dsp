/* dsp_ContainerGroup */
select cast(cg.id as varchar) as key,
    array[
        /* address.view */
        a.view_,
        /* lkCode */
        ca.lk_code
    ] as search_values,

    array[
        /* address.view */
        concat(
            a.view_, ' - ',
            '<span class=\"ant-typography\"><strong>', cg.amount, '</strong>&#160;',
            '<span class=\"ant-typography ant-typography-secondary\" style=\"font-size: 13px;\">', ct.volume, '</span></span>'
        ),
        /* lkCode */
        concat(
            ca.lk_code, ' (', a.view_, ') - ',
            '<span class=\"ant-typography\"><strong>', cg.amount, '</strong>&#160;',
            '<span class=\"ant-typography ant-typography-secondary\" style=\"font-size: 13px;\">', ct.volume, '</span></span>'
        )
    ] as display_values,

    (cg.delete_ts is not null or cg.deleted_from_lk_ts is not null
        or ca.delete_ts is not null or coalesce(ca.is_archived, false)
    ) as is_archive,

    array[
        (select concat('region.id=', string_agg(distinct cast(l.region_id as varchar), ','))
        from dsp_region_container_area_link as l
        where l.container_area_id = ca.id)
    ] as tags
from dsp_container_group as cg
    join dsp_container_area as ca on ca.id = cg.container_area_id
    join dsp_address as a on a.id = ca.address_id
    join dsp_container_type as ct on ct.id = cg.container_type_id
