/* dsp_ContainerGroup(taskDetail.task[vehicle,carrier,region]) */
with cg as (
    select cg.id,
        cg.delete_ts,
        cg.deleted_from_lk_ts,
        cg.container_area_id,
        cg.container_type_id,
        cg.amount,
        t.vehicle_id as taskdetail_task_vehicle_id,
        t.carrier_id as taskdetail_task_carrier_id,
        t.region_id as taskdetail_task_region_id,
        string_agg(distinct to_char(t.start_date, 'YYYY-MM-DD'), ',') as taskdetail_task_start_dates
    from dsp_container_group as cg
        join dsp_task_detail_container_group_link as l on l.container_group_id = cg.id
        join dsp_task_detail as td on td.id = l.task_detail_id
        join dsp_task as t on t.id = td.task_id
    where t.start_date >= now() at time zone 'UTC' - interval '45 days'
    group by cg.id,
        t.vehicle_id,
        t.carrier_id,
        t.region_id
)
select concat(
        cast(cg.id as varchar), ',',
        cast(cg.taskdetail_task_vehicle_id as varchar), ',',
        cast(cg.taskdetail_task_carrier_id as varchar), ',',
        cast(cg.taskdetail_task_region_id as varchar)
    ) as key,

    array[
        /* address.view */
        a.view_,
        /* lkCode */
        ca.lk_code
    ] as search_values,

    array[
        /* address.view */
        concat(
            '<strong>', vn.without_region, '</strong>: ', a.view_, ' - ',
            '<span class=\"ant-typography\"><strong>', cg.amount, '</strong>&#160;',
            '<span class=\"ant-typography ant-typography-secondary\" style=\"font-size: 13px;\">', ct.volume, '</span></span>'
        ),
        /* lkCode */
        concat(
            '<strong>', vn.without_region, '</strong>: ', ca.lk_code, ' (', a.view_, ') - ',
            '<span class=\"ant-typography\"><strong>', cg.amount, '</strong>&#160;',
            '<span class=\"ant-typography ant-typography-secondary\" style=\"font-size: 13px;\">', ct.volume, '</span></span>'
        )
    ] as display_values,

    (cg.delete_ts is not null or cg.deleted_from_lk_ts is not null
        or ca.delete_ts is not null or coalesce(ca.is_archived, false)
    ) as is_archive,

    array[
        concat('taskDetail.task.carrier.id=', cg.taskdetail_task_carrier_id), 
        concat('taskDetail.task.region.id=', cg.taskdetail_task_region_id), 
        concat('taskDetail.task.startDate=', cg.taskdetail_task_start_dates)
    ] as tags
from cg
    join dsp_container_area as ca on ca.id = cg.container_area_id
    join dsp_address as a on a.id = ca.address_id
    join dsp_container_type as ct on ct.id = cg.container_type_id
    join dsp_vehicle as v on v.id = cg.taskdetail_task_vehicle_id
    join dsp_vehicle_number vn on vn.id = v.number_id
