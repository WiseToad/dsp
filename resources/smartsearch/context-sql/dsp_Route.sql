/* dsp_Route */
select cast(r.id as varchar) as key,
    array[
        /* name */
        r.name
    ] as search_values,

    array[
        /* name */
        r.name
    ] as display_values,

    r.delete_ts is not null as is_archive,

    array[
        concat('carrier.id=', r.carrier_id),
        concat('region.id=', r.region_id)
    ] as tags
from dsp_route as r
