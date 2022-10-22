/* dsp_Route(routeDetail.group) */
select concat(
        cast(r.id as varchar), ',',
        cast(cg.id as varchar)
    ) as key,
    
    array[
        /* routeDetail.group.address.view */
        a.view_,
        /* routeDetail.group.lkCode */
        ca.lk_code
    ] as search_values,

    array[
        /* routeDetail.group.address.view */
        concat(
            '<strong>', r.name, '</strong>: ', a.view_, ' - ',
            '<span class=\"ant-typography\"><strong>', cg.amount, '</strong>&#160;',
            '<span class=\"ant-typography ant-typography-secondary\" style=\"font-size: 13px;\">', ct.volume, '</span></span>'
        ),
        /* routeDetail.group.lkCode */
        concat(
            '<strong>', r.name, '</strong>: ', ca.lk_code, ' (', a.view_, ') - ',
            '<span class=\"ant-typography\"><strong>', cg.amount, '</strong>&#160;',
            '<span class=\"ant-typography ant-typography-secondary\" style=\"font-size: 13px;\">', ct.volume, '</span></span>'
        )
    ] as display_values,

    (cg.delete_ts is not null or cg.deleted_from_lk_ts is not null
        or ca.delete_ts is not null or coalesce(ca.is_archived, false)
    ) as is_archive,

    array[
        concat('carrier.id=', r.carrier_id), 
        concat('region.id=', r.region_id)
    ] as tags
from dsp_route as r
    join dsp_route_detail as rd on rd.route_id = r.id
    join dsp_container_group as cg on cg.id = rd.group_id
    join dsp_container_area as ca on ca.id = cg.container_area_id
    join dsp_address as a on a.id = ca.address_id
    join dsp_container_type as ct on ct.id = cg.container_type_id
