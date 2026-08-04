alter table activity_delivery drop constraint activity_delivery_state_check;
alter table activity_delivery add constraint activity_delivery_state_check
    check (state in ('PENDING', 'RETRY', 'PROCESSING', 'SENT', 'FAILED', 'DISCARDED', 'UNCERTAIN_SENT'));
alter table activity_delivery add column next_attempt_at timestamptz not null default current_timestamp;
alter table activity_delivery add column attempts integer not null default 0;
alter table activity_delivery add column processing_started_at timestamptz;
update activity_delivery set state = 'PENDING', next_attempt_at = created_at where state = 'PROCESSING';
create index activity_delivery_due_idx on activity_delivery (next_attempt_at) where state in ('PENDING', 'RETRY');
