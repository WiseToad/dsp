/* dsp_Task */
select distinct cast(t.id as varchar) as key,
    array[
        /* driver.person.fullName */
        p.full_name,
        /* vehicle.garageNumber */
        v.garage_number,
        /* vehicle.number.withoutRegion */
        vn.without_region
    ] as search_values,

    array[
        /* driver.person.fullName */
        p.full_name,              
        /* vehicle.garageNumber */
        concat('<strong>', vn.without_region, '</strong> (гаражный № ', coalesce(v.garage_number, 'отсутствует'), ')'),
        /* vehicle.number.withoutRegion */
        concat('<strong>', vn.without_region, '</strong>')
    ] as display_values,
    
    v.delete_ts is not null as is_archive,

    array[
        concat('carrier.id=', t.carrier_id),
        concat('region.id=', t.region_id),
        concat('startDate=', to_char(t.start_date, 'YYYY-MM-DD'))
    ] as tags
from dsp_task as t
    join dsp_vehicle as v on v.id = t.vehicle_id
    join dsp_vehicle_number as vn on vn.id = v.number_id
    left join dsp_employee as e on e.id = t.driver_id
    left join dsp_person as p on p.id = e.person_id
where t.start_date >= now() at time zone 'UTC' - interval '45 days'
