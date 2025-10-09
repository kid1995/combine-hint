create table if not exists hint
(
	id                  bigint primary key generated always as identity not null,
	hint_source					text																						not null,
	message  						text																						not null,
	field_id_source     text																						not null,
	hint_category				text																						not null,
	show_to_user				boolean																					,
	creation_date       timestamp                                       not null,
	process_id					text                                         		not null,
	process_version			text                                            ,
	resource_id					text
);

create index if not exists idx_hint_process_id_hint_source on hint (process_id, hint_source);
