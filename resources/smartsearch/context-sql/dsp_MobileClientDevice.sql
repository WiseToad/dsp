/* dsp_MobileClientDevice */
select cast(md.id as varchar) as key,
    array[
        /* id */
        upper(right(cast(md.dev_uuid as varchar), 12))
    ] as search_values,

    array[
        /* id */
        upper(right(cast(md.dev_uuid as varchar), 12))
    ] as display_values,

    md.delete_ts is not null as is_archive,

    cast(array[] as varchar[]) as tags
from dsp_mobile_client_device as md
