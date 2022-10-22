/* dsp_Verification */
select cast(v.id as varchar) as key,
    array[
        /* address */
        v.address
    ] as search_values,

    array[
        /* address */
        v.address
    ] as display_values,

    v.delete_ts is not null as is_archive,

    cast(array[] as varchar[]) as tags
from dsp_verification as v
where exists (
    select null
    from dsp_inventorization_detail as d
        join dsp_inventorization as i on i.id = d.inventorization_id
    where d.verification_id = v.id
)
