/* dsp_Vehicle */
select cast(v.id as varchar) as key,
    array[
        /* garageNumber */
        v.garage_number,
        /* number.withoutRegion */
        vn.without_region
    ] as search_values,

    array[
        /* garageNumber */
        concat(vn.without_region, ' (гаражный № ', coalesce(v.garage_number, 'отсутствует'), ')'),
        /* number.withoutRegion */
        vn.without_region
    ] as display_values,

    v.delete_ts is not null as is_archive,

    array[
        concat('owner.id=', v.owner_id)
    ] as tags
from dsp_vehicle as v
    join dsp_vehicle_number as vn on vn.id = v.number_id
