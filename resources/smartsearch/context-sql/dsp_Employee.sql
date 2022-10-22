/* dsp_Employee */
select cast(e.id as varchar) as key,
    array[
        /* email */
        e.email,
        /* person.fullName */
        p.full_name
    ] as search_values,

    array[
        /* email */
        e.email,
        /* person.fullName */
        p.full_name
    ] as display_values,

    e.delete_ts is not null as is_archive,

    array[
        (select concat('user.authority.name=', string_agg(distinct a.authority_name, ','))
        from sys_user_authority as a
        where a.user_id = e.user_id)
    ] as tags
from dsp_employee as e
    join dsp_person as p on p.id = e.person_id
