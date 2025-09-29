alter table hint
	add column if not exists mongo_uuid text;

create table if not exists migration_job
(
	id                  bigint primary key generated always as identity not null,
	message             text,
	last_merged_point   text,
	data_set_start_date timestamp,
	data_set_end_date   timestamp,
	creation_date       timestamp                                       not null,
	finishing_date      timestamp,
	state               text                                            not null,
	resolved						boolean default false														not null
);

create table if not exists migration_error
(
	id         					bigint primary key generated always as identity not null,
	message    					text,
	mongo_uuid 					text,
	resolved						boolean,
	job							bigint																						not null
);

alter table migration_error
	add constraint fk_migration_job
		foreign key (job) references migration_job (id);
