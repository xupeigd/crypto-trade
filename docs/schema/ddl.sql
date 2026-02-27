create table if not exists flyway_schema_history
(
    installed_rank int                                 not null
        primary key,
    version        varchar(50)                         null,
    description    varchar(200)                        not null,
    type           varchar(20)                         not null,
    script         varchar(1000)                       not null,
    checksum       int                                 null,
    installed_by   varchar(100)                        not null,
    installed_on   timestamp default CURRENT_TIMESTAMP not null,
    execution_time int                                 not null,
    success        tinyint(1)                          not null
);

create index flyway_schema_history_s_idx
    on flyway_schema_history (success);

create table if not exists t_account_equity_snapshot
(
    equity_id             bigint auto_increment
        primary key,
    api_key_id            bigint         not null,
    available_equity_usdt decimal(38, 8) not null,
    created_time          datetime(6)    null,
    frozen_equity_usdt    decimal(38, 8) null,
    margin_equity_usdt    decimal(38, 8) null,
    total_equity_usdt     decimal(38, 8) not null,
    update_time           datetime(6)    not null,
    updated_time          datetime(6)    null,
    constraint UKdk64scbibng34gvkujvdgwp14
        unique (api_key_id)
);

create table if not exists t_ai_model_configs
(
    config_id       bigint auto_increment
        primary key,
    cost_per_token  decimal(10, 6) null,
    created_time    datetime(6)    not null,
    default_model   bit            not null,
    description     text           null,
    display_name    varchar(200)   not null,
    is_active       bit            not null,
    max_tokens      bigint         null,
    model_id        varchar(100)   not null,
    parameter_size  int            null,
    provider        varchar(50)    not null,
    updated_time    datetime(6)    not null,
    api_format      varchar(50)    null,
    api_key         varchar(500)   null,
    api_url         varchar(500)   null,
    max_concurrent  int            not null,
    model_type      varchar(20)    not null,
    retry_count     int            not null,
    timeout_seconds int            not null,
    extra_body      text           null,
    constraint UK_n4xtdjcr5vxi6bufd5ftt4rlu
        unique (model_id)
);

create table if not exists t_cex_api_call_records
(
    id             bigint auto_increment
        primary key,
    api_path       varchar(500)                                                                                                      not null,
    api_type       enum ('PLACE_ORDER', 'CLOSE_POSITION', 'CANCEL_ORDER', 'SET_ALGO_ORDER', 'AMEND_ALGO_ORDER', 'CANCEL_ALGO_ORDER') not null,
    call_time      datetime(6)                                                                                                       not null,
    create_time    datetime(6)                                                                                                       not null,
    duration_ms    bigint                                                                                                            null,
    error_message  text                                                                                                              null,
    exchange       enum ('OKX', 'BINANCE', 'BYBIT')                                                                                  not null,
    http_method    enum ('GET', 'POST', 'PUT', 'DELETE')                                                                             not null,
    http_status    int                                                                                                               null,
    inst_id        varchar(100)                                                                                                      null,
    order_id       varchar(100)                                                                                                      null,
    order_type     enum ('MARKET', 'LIMIT', 'TAKE_PROFIT', 'STOP_LOSS')                                                              null,
    request_params longtext                                                                                                          null,
    response_body  longtext                                                                                                          null,
    response_time  datetime(6)                                                                                                       null,
    status         enum ('PENDING', 'SUCCESS', 'FAILED', 'TIMEOUT')                                                                  not null,
    update_time    datetime(6)                                                                                                       null,
    api_key_id     bigint                                                                                                            null
);

create index idx_api_type
    on t_cex_api_call_records (api_type);

create index idx_call_time
    on t_cex_api_call_records (call_time);

create index idx_create_time
    on t_cex_api_call_records (create_time);

create index idx_exchange
    on t_cex_api_call_records (exchange);

create index idx_inst_id
    on t_cex_api_call_records (inst_id);

create index idx_order_id
    on t_cex_api_call_records (order_id);

create index idx_status
    on t_cex_api_call_records (status);

create table if not exists t_cex_api_keys
(
    key_id          bigint auto_increment
        primary key,
    access_key      varchar(200)       not null,
    cex_name        varchar(50)        not null,
    created_time    datetime(6)        null,
    description     varchar(500)       null,
    is_live_trading bit                not null,
    pass_phrase     varchar(200)       null,
    secret_key      varchar(200)       not null,
    status          varchar(20)        null,
    storage_type    enum ('DB', 'ENV') not null,
    updated_time    datetime(6)        null
);

create table if not exists del_t_trading_orders
(
    id                  bigint auto_increment
        primary key,
    amt                 decimal(38, 8) not null,
    api_key_id          bigint         null,
    avg_px              decimal(38, 8) null,
    ccy                 varchar(10)    not null,
    created_time        datetime(6)    null,
    error_msg           text           null,
    exec_time           datetime(6)    null,
    fee                 decimal(38, 8) null,
    fee_ccy             varchar(10)    null,
    filled_amt          decimal(38, 8) null,
    filled_sz           decimal(38, 8) null,
    inst_id             varchar(50)    not null,
    lever               decimal(8, 2)  not null,
    order_id            varchar(50)    not null,
    order_result        varchar(50)    null,
    order_state         varchar(20)    not null,
    order_type          varchar(20)    not null,
    pos_side            varchar(20)    null,
    px                  decimal(38, 8) null,
    side                varchar(20)    not null,
    source              varchar(20)    null,
    stop_loss_pct       decimal(8, 4)  null,
    stop_loss_price     decimal(38, 8) null,
    sz                  decimal(38, 8) not null,
    take_profit_pct     decimal(8, 4)  null,
    take_profit_price   decimal(38, 8) null,
    td_mode             varchar(20)    not null,
    updated_time        datetime(6)    null,
    vendor              varchar(10)    not null,
    bot_id              bigint         null,
    cex_order_id        varchar(50)    null,
    completed_time      datetime(6)    null,
    order_status        varchar(20)    not null,
    order_uuid          varchar(50)    not null,
    risk_control_id     bigint         null,
    stop_loss_enabled   bit            null,
    strategy_id         bigint         null,
    submitted_time      datetime(6)    null,
    take_profit_enabled bit            null,
    constraint UK_c8q87ivunqj7xj5mof17b7vrv
        unique (order_id),
    constraint FK9fl6t0xo1umg777vxojdm3ltf
        foreign key (api_key_id) references t_cex_api_keys (key_id)
);

create table if not exists t_cex_balances
(
    balance_id          bigint auto_increment
        primary key,
    api_key_id          bigint         null,
    available_balance   decimal(38, 8) not null,
    cex_name            varchar(50)    not null,
    created_time        datetime(6)    null,
    currency            varchar(10)    not null,
    data_ingestion_time datetime(6)    not null,
    locked_balance      decimal(38, 8) not null,
    total_balance       decimal(38, 8) not null,
    updated_time        datetime(6)    null,
    usd_value           decimal(38, 8) null
);

create table if not exists t_cex_instruments
(
    id                  bigint auto_increment
        primary key,
    alias               varchar(100) null,
    base_ccy            varchar(20)  null,
    category            varchar(20)  null,
    created_at          datetime(6)  null,
    ct_mult             varchar(50)  null,
    ct_val              varchar(50)  null,
    ct_val_ccy          varchar(20)  null,
    data_ingestion_time datetime(6)  not null,
    exp_time            varchar(50)  null,
    fee_rate            varchar(20)  null,
    inst_id             varchar(100) not null,
    inst_type           varchar(20)  not null,
    is_leverage         varchar(10)  null,
    lever               varchar(20)  null,
    list_time           varchar(50)  null,
    lot_sz              varchar(50)  null,
    max_lmt             varchar(50)  null,
    max_lmt_sz          varchar(50)  null,
    max_mkt             varchar(50)  null,
    max_mkt_sz          varchar(50)  null,
    max_ts_sz           varchar(50)  null,
    min_sz              varchar(50)  null,
    opt_type            varchar(10)  null,
    position_idx        varchar(20)  null,
    provider            varchar(20)  not null,
    quote_ccy           varchar(20)  null,
    settle_ccy          varchar(20)  null,
    state               varchar(20)  null,
    stk                 varchar(50)  null,
    tick_sz             varchar(50)  null,
    updated_at          datetime(6)  null,
    is_live_trading     bit          not null,
    constraint UK4yjaj7tgm0fkjesfbl8suu5e5
        unique (provider, inst_id),
    constraint UKs3wpr5ocermyorr2aw9533e0g
        unique (provider, inst_id, is_live_trading)
);

create table if not exists t_cex_trading_orders
(
    id               bigint auto_increment
        primary key,
    acc_fill_sz      varchar(50)    null,
    amt              decimal(38, 8) null,
    api_key_id       bigint         not null,
    avg_px           decimal(38, 8) null,
    c_time           datetime(6)    null,
    ccy              varchar(10)    null,
    cl_ord_id        varchar(50)    null,
    created_time     datetime(6)    null,
    exchange         varchar(10)    not null,
    exec_time        datetime(6)    null,
    fee              decimal(38, 8) null,
    fee_ccy          varchar(10)    null,
    fill_ratio       decimal(8, 4)  null,
    filled_amt       decimal(38, 8) null,
    filled_sz        decimal(38, 8) null,
    inst_id          varchar(50)    not null,
    inst_type        varchar(20)    null,
    last_sync_time   datetime(6)    null,
    lever            decimal(8, 2)  null,
    order_id         varchar(50)    not null,
    order_state      varchar(20)    not null,
    order_type       varchar(20)    not null,
    pos_side         varchar(20)    null,
    px               decimal(38, 8) null,
    rebate           decimal(38, 8) null,
    rebate_ccy       varchar(10)    null,
    side             varchar(20)    not null,
    state_msg        varchar(100)   null,
    sync_error_msg   text           null,
    sync_retry_count int            null,
    sync_status      varchar(20)    null,
    sz               decimal(38, 8) null,
    td_mode          varchar(20)    null,
    u_time           datetime(6)    null,
    updated_time     datetime(6)    null,
    action_id        bigint         null,
    record_id        bigint         null,
    constraint UK_80rmora2mhfaamgbk3co61ce2
        unique (order_id)
);

create table if not exists t_chat_sessions
(
    session_id   bigint auto_increment
        primary key,
    created_time datetime(6)  null,
    model_name   varchar(50)  null,
    session_name varchar(100) null,
    status       varchar(20)  null,
    updated_time datetime(6)  null,
    user_id      varchar(50)  null
);

create table if not exists t_chat_messages
(
    message_id         bigint auto_increment
        primary key,
    content            text        not null,
    created_time       datetime(6) null,
    processing_time_ms bigint      null,
    role               varchar(20) not null,
    tokens_used        int         null,
    session_id         bigint      not null,
    constraint FK68dtlidayv2g2p21p1p2xbn12
        foreign key (session_id) references t_chat_sessions (session_id)
);

create table if not exists t_conversation_actions
(
    id                 bigint auto_increment
        primary key,
    action_parameters  text         null,
    action_result      text         null,
    action_type        varchar(50)  not null,
    created_time       datetime(6)  null,
    decision_id        varchar(255) not null,
    error_message      text         null,
    message_id         varchar(255) not null,
    processing_time_ms bigint       null,
    session_id         varchar(255) not null,
    status             varchar(20)  not null,
    updated_time       datetime(6)  null
);

create table if not exists t_conversation_messages
(
    message_id                bigint auto_increment
        primary key,
    api_key_id                bigint         null,
    completion_tokens         int            null,
    confidence_score          int            null,
    content                   longtext       null,
    created_time              datetime(6)    not null,
    decision_action           varchar(20)    null,
    decision_price            decimal(20, 8) null,
    decision_quantity         decimal(20, 8) null,
    error_message             text           null,
    llm_call_time_ms          bigint         null,
    model_name                varchar(100)   null,
    parent_message_id         bigint         null,
    post_action_time_ms       bigint         null,
    processing_time_ms        bigint         null,
    prompt_generation_time_ms bigint         null,
    prompt_tokens             int            null,
    role                      varchar(20)    not null,
    session_id                bigint         not null,
    status                    varchar(20)    null,
    target_inst_id            varchar(50)    null,
    tool_calls                json           null,
    tool_name                 varchar(100)   null,
    total_tokens              int            null,
    updated_time              datetime(6)    null
);

create index idx_api_key_id
    on t_conversation_messages (api_key_id);

create index idx_model_name
    on t_conversation_messages (model_name);

create index idx_parent_message_id
    on t_conversation_messages (parent_message_id);

create index idx_role
    on t_conversation_messages (role);

create index idx_session_created
    on t_conversation_messages (session_id, created_time);

create index idx_session_id
    on t_conversation_messages (session_id);

create index idx_status
    on t_conversation_messages (status);

create table if not exists t_funding_rate_data
(
    id                  bigint auto_increment
        primary key,
    created_at          datetime(6) null,
    data_ingestion_time datetime(6) not null,
    funding_rate        varchar(20) null,
    funding_time        varchar(50) null,
    inst_id             varchar(50) not null,
    inst_type           varchar(20) not null,
    next_funding_rate   varchar(20) null,
    updated_at          datetime(6) null,
    vendor              varchar(10) not null
);

create table if not exists t_futures_ticker_data
(
    id                  bigint auto_increment
        primary key,
    ask_price           decimal(38, 8) null,
    ask_sz              decimal(38, 8) null,
    bid_price           decimal(38, 8) null,
    bid_sz              decimal(38, 8) null,
    data_ingestion_time datetime(6)    not null,
    high_24h            decimal(38, 8) null,
    inst_id             varchar(50)    not null,
    inst_type           varchar(20)    not null,
    last                decimal(38, 8) null,
    last_sz             decimal(38, 8) null,
    low_24h             decimal(38, 8) null,
    open_24h            decimal(38, 8) null,
    sod_utc0            decimal(38, 8) null,
    sod_utc8            decimal(38, 8) null,
    ts                  bigint         null,
    ts_mins_str         varchar(20)    null,
    vendor              varchar(10)    not null,
    vol_24h             decimal(38, 8) null,
    vol_ccy_24h         decimal(38, 8) null,
    ts_hour_str         varchar(20)    null,
    is_live_trading     bit            not null,
    constraint uk_vendor_inst_id_hour
        unique (vendor, is_live_trading, inst_id, ts_hour_str),
    constraint uk_vendor_inst_id_hour_trading
        unique (vendor, inst_id, ts_hour_str, is_live_trading)
);

create table if not exists t_kline_data
(
    id           bigint auto_increment
        primary key,
    base_asset   varchar(20)          null,
    close_price  decimal(20, 8)       null,
    created_at   datetime(6)          null,
    high_price   decimal(20, 8)       null,
    inst_id      varchar(100)         not null,
    kline_time   bigint               not null,
    low_price    decimal(20, 8)       null,
    open_price   decimal(20, 8)       null,
    provider     varchar(20)          not null,
    quote_asset  varchar(20)          null,
    quote_volume decimal(30, 8)       null,
    timeframe    varchar(10)          not null,
    updated_at   datetime(6)          null,
    volume       decimal(30, 8)       null,
    confirm      tinyint(1) default 0 null,
    constraint UKfnjspxprt61oelmqfemiarn38
        unique (provider, inst_id, timeframe, kline_time)
);

create table if not exists t_llm_audit_logs
(
    id                 bigint auto_increment
        primary key,
    ai_response        longtext                   null,
    api_key_id         bigint                     not null,
    call_stats_id      bigint                     not null,
    call_status        enum ('SUCCESS', 'FAILED') not null,
    created_time       datetime(6)                not null,
    error_message      text                       null,
    model_name         varchar(100)               not null,
    processing_time_ms bigint                     null,
    prompt_content     longtext                   not null,
    session_id         varchar(100)               not null,
    updated_time       datetime(6)                not null
);

create table if not exists t_llm_call_records
(
    id                        bigint auto_increment
        primary key,
    api_key_id                bigint         not null,
    call_count                int            null,
    call_end_time             datetime(6)    null,
    call_source               varchar(20)    null,
    call_start_time           datetime(6)    null,
    conversation_state        varchar(50)    null,
    created_at                datetime(6)    not null,
    decision_action           varchar(20)    null,
    decision_confidence       decimal(5, 2)  null,
    decision_price            decimal(20, 8) null,
    decision_quantity         decimal(38, 2) null,
    error_message             text           null,
    is_executed               bit            null,
    model_name                varchar(100)   null,
    parent_id                 bigint         null,
    processing_time_ms        bigint         null,
    response_content          text           null,
    round_number              int            null,
    session_id                bigint         null,
    user_message_id           bigint         null comment '用户消息ID(关联t_chat_messages.message_id, role=user)',
    assistant_message_id      bigint         null comment '助手消息ID(关联t_chat_messages.message_id, role=assistant)',
    status                    varchar(20)    null,
    target_inst_id            varchar(50)    null,
    updated_at                datetime(6)    null,
    llm_call_time_ms          bigint         null,
    post_action_time_ms       bigint         null,
    prompt_generation_time_ms bigint         null
);

create index idx_api_key_id
    on t_llm_call_records (api_key_id);

create index idx_assistant_message_id
    on t_llm_call_records (assistant_message_id);

create index idx_call_start_time
    on t_llm_call_records (call_start_time);

create index idx_parent_id
    on t_llm_call_records (parent_id);

create index idx_session_id
    on t_llm_call_records (session_id);

create index idx_status
    on t_llm_call_records (status);

create index idx_user_message_id
    on t_llm_call_records (user_message_id);

create table if not exists t_llm_call_stats
(
    id                 bigint auto_increment
        primary key,
    api_key_id         bigint       not null,
    call_count         int          not null,
    created_time       datetime(6)  null,
    current_call_time  datetime(6)  not null,
    error_message      text         null,
    last_call_time     datetime(6)  null,
    model_name         varchar(100) not null,
    processing_time_ms bigint       null,
    session_id         varchar(100) null,
    success            bit          not null,
    updated_time       datetime(6)  null,
    processing_status  varchar(20)  not null,
    raw_response       text         null
);

create table if not exists t_order_executions
(
    id           bigint auto_increment
        primary key,
    created_time datetime(6)    null,
    exec_fee     decimal(38, 8) null,
    exec_fee_ccy varchar(10)    null,
    exec_price   decimal(38, 8) null,
    exec_sz      decimal(38, 8) null,
    exec_ts      datetime(6)    null,
    exec_type    varchar(20)    null,
    exec_value   decimal(38, 8) null,
    execution_id varchar(50)    null,
    order_id     varchar(50)    not null,
    trade_id     varchar(50)    null
);

create table if not exists t_position_snapshot
(
    snapshot_id         bigint auto_increment
        primary key,
    api_key_id          bigint         not null,
    avail_pos           decimal(38, 8) null,
    avg_px              decimal(38, 8) null,
    ccy                 varchar(255)   null,
    close_avg_px        decimal(38, 2) null,
    close_total_pos     decimal(38, 8) null,
    created_time        datetime(6)    null,
    ctime               bigint         null,
    data_ingestion_time bigint         null,
    fee                 decimal(38, 8) null,
    funding_fee         decimal(38, 8) null,
    imr                 decimal(38, 8) null,
    inst_id             varchar(255)   not null,
    inst_type           varchar(255)   not null,
    last_px             decimal(38, 8) null,
    lever               decimal(8, 2)  null,
    liq_px              decimal(38, 8) null,
    margin              decimal(38, 8) null,
    mark_px             decimal(38, 8) null,
    mgn_mode            varchar(255)   null,
    mgn_ratio           decimal(38, 8) null,
    mmr                 decimal(38, 8) null,
    notional_usd        decimal(38, 8) null,
    open_avg_px         decimal(38, 2) null,
    open_max_pos        decimal(38, 8) null,
    pnl_ratio           decimal(38, 8) null,
    pos                 decimal(38, 8) not null,
    pos_id              varchar(255)   null,
    pos_side            varchar(255)   not null,
    realized_pnl        decimal(38, 8) null,
    settled_pnl         decimal(38, 8) null,
    type                varchar(255)   null,
    update_time         datetime(6)    not null,
    updated_time        datetime(6)    null,
    upl                 decimal(38, 8) null,
    upl_last_px         decimal(38, 8) null,
    utime               bigint         null
);

create table if not exists t_proxy_service_configs
(
    proxy_id     bigint auto_increment
        primary key,
    created_time datetime(6)  not null,
    description  varchar(500) null,
    proxy_name   varchar(100) not null,
    proxy_type   varchar(20)  not null,
    server_host  varchar(200) not null,
    server_port  int          not null,
    status       varchar(20)  not null,
    updated_time datetime(6)  not null
);

create table if not exists t_data_fetch_configs
(
    config_id            bigint auto_increment
        primary key,
    api_path             varchar(200) not null,
    auth_key_id          bigint       null,
    cex_base_url         varchar(200) not null,
    data_processor_class varchar(200) not null,
    http_method          varchar(10)  not null,
    proxy_id             bigint       null,
    request_params       text         null,
    requires_auth        bit          not null,
    requires_proxy       bit          not null,
    response_mapping     text         null,
    signature_class      varchar(200) null,
    target_duckdb_table  varchar(100) not null,
    task_id              bigint       not null,
    constraint UK_4wv5ax86xu2ttna5n7enhw73r
        unique (task_id),
    constraint FK1cnlxm82ntv3ds3t6hnvtclx0
        foreign key (proxy_id) references t_proxy_service_configs (proxy_id)
);

create table if not exists t_risk_control_config
(
    config_id             bigint                                                                               not null
        primary key,
    current_risk_mode     enum ('AUTO', 'MANUAL')                                                              not null,
    current_trading_style enum ('C1_CONSERVATIVE', 'C2_CAUTIOUS', 'C3_MODERATE', 'C4_ACTIVE', 'C5_AGGRESSIVE') not null,
    default_risk_mode     enum ('AUTO', 'MANUAL')                                                              not null,
    default_trading_style enum ('C1_CONSERVATIVE', 'C2_CAUTIOUS', 'C3_MODERATE', 'C4_ACTIVE', 'C5_AGGRESSIVE') not null,
    update_time           datetime(6)                                                                          null
);

create table if not exists t_risk_control_orders
(
    order_id                bigint auto_increment
        primary key,
    api_key_id              bigint                                   not null,
    audit_status            enum ('PENDING', 'APPROVED', 'REJECTED') not null,
    audit_time              datetime(6)                              null,
    auditor                 varchar(100)                             null,
    create_time             datetime(6)                              not null,
    lever                   decimal(10, 2)                           null,
    order_source            varchar(50)                              not null,
    order_type              enum ('LIMIT', 'MARKET')                 not null,
    original_amount         decimal(20, 8)                           null,
    original_order_id       varchar(100)                             not null,
    pos_side                varchar(20)                              null,
    price                   decimal(20, 8)                           null,
    quantity                decimal(20, 8)                           not null,
    rejection_reason        varchar(500)                             null,
    risk_level              enum ('HIGH', 'MEDIUM', 'LOW')           not null,
    side                    enum ('BUY', 'SELL')                     not null,
    stop_loss_price         decimal(20, 8)                           null,
    symbol                  varchar(50)                              not null,
    take_profit_price       decimal(20, 8)                           null,
    update_time             datetime(6)                              null,
    estimated_total_capital decimal(20, 8)                           null,
    action_id               bigint                                   null,
    constraint UK_igrbqdsbunsceoqmpug3a10lx
        unique (original_order_id)
);

create table if not exists t_risk_mode_history
(
    history_id    bigint auto_increment
        primary key,
    change_reason varchar(500)            null,
    created_time  datetime(6)             null,
    new_mode      enum ('AUTO', 'MANUAL') not null,
    old_mode      enum ('AUTO', 'MANUAL') not null,
    operator_info varchar(200)            null
);

create table if not exists t_scheduled_tasks
(
    task_id         bigint auto_increment
        primary key,
    created_time    datetime(6)  null,
    cron_expression varchar(50)  null,
    description     varchar(500) null,
    parameters      text         null,
    parent_task_id  bigint       null,
    status          varchar(20)  null,
    task_name       varchar(100) not null,
    task_type       varchar(20)  not null,
    timeout_seconds int          null,
    updated_time    datetime(6)  null,
    constraint UK_nar4h1vqwf6oljhtekkmj6r6p
        unique (task_name)
);

create table if not exists t_task_executions
(
    execution_id        bigint auto_increment
        primary key,
    actual_execute_time datetime(6)  null,
    created_time        datetime(6)  null,
    error_message       text         null,
    execution_result    text         null,
    execution_status    varchar(20)  not null,
    finish_time         datetime(6)  null,
    parent_task_name    varchar(100) not null,
    task_id             bigint       not null,
    trigger_time        datetime(6)  not null,
    trigger_type        varchar(20)  not null
);

create index idx_execution_status_created
    on t_task_executions (execution_status, created_time);

create index idx_task_execution_lookup
    on t_task_executions (task_id, parent_task_name, execution_status, created_time);

create table if not exists t_trade_actions
(
    id                  bigint auto_increment
        primary key,
    action_type         varchar(50)    null,
    amount              decimal(38, 2) null,
    api_key_id          bigint         not null,
    confidence          int            null,
    create_time         datetime(6)    not null,
    error_message       text           null,
    executed_price      decimal(20, 8) null,
    executed_size       decimal(20, 8) null,
    executed_time       datetime(6)    null,
    execution_source    varchar(20)    null,
    execution_time_ms   bigint         null,
    inst_id             varchar(50)    null,
    lever               int            null,
    model_id            varchar(100)   null,
    open_close          tinyint        null,
    order_id            varchar(100)   null,
    order_type          tinyint        null,
    parent_action_id    bigint         null,
    pos_side            varchar(20)    null,
    price               decimal(20, 8) null,
    priority            int            null,
    quantity            decimal(20, 8) null,
    query_limit         int            null,
    reasoning           text           null,
    record_id           bigint         not null,
    replay_count        int            null,
    status              varchar(20)    null,
    stop_loss           decimal(20, 8) null,
    take_profit         decimal(20, 8) null,
    timeframe           varchar(20)    null,
    update_time         datetime(6)    null,
    risk_control_id     bigint         null,
    risk_control_status varchar(20)    null,
    check (`open_close` between 0 and 1),
    check (`order_type` between 0 and 1)
);

create table if not exists t_trade_balance_snapshots
(
    snapshot_id               bigint auto_increment
        primary key,
    api_key_id                bigint         not null,
    available_equity_usdt     decimal(38, 8) not null,
    cex_name                  varchar(50)    not null,
    created_time              datetime(6)    null,
    margin_ratio              decimal(10, 4) null,
    max_available_amount      decimal(38, 8) null,
    snapshot_time             datetime(6)    not null,
    source                    varchar(20)    not null,
    total_equity_usdt         decimal(38, 8) not null,
    unrealized_pnl_usdt       decimal(38, 8) null,
    updated_time              datetime(6)    null,
    used_margin_usdt          decimal(38, 8) not null,
    record_id                 bigint         null,
    display_total_equity_usdt decimal(38, 8) null
);

create table if not exists t_trading_orders
(
    id                  bigint auto_increment
        primary key,
    amt                 decimal(38, 8) not null,
    api_key_id          bigint         null,
    bot_id              bigint         null,
    cex_order_id        varchar(50)    null,
    completed_time      datetime(6)    null,
    created_time        datetime(6)    null,
    error_msg           text           null,
    inst_id             varchar(50)    not null,
    lever               decimal(8, 2)  not null,
    order_status        varchar(20)    not null,
    order_type          varchar(20)    not null,
    order_uuid          varchar(50)    not null,
    pos_side            varchar(20)    null,
    risk_control_id     bigint         null,
    side                varchar(20)    not null,
    source              varchar(20)    null,
    stop_loss_enabled   bit            null,
    stop_loss_pct       decimal(8, 4)  null,
    stop_loss_price     decimal(38, 8) null,
    strategy_id         bigint         null,
    submitted_time      datetime(6)    null,
    sz                  decimal(38, 8) null,
    take_profit_enabled bit            null,
    take_profit_pct     decimal(8, 4)  null,
    take_profit_price   decimal(38, 8) null,
    updated_time        datetime(6)    null,
    action_id           bigint         null,
    record_id           bigint         null,
    constraint UK_7qsvpcgd50xfpm2lsfgn890hp
        unique (order_uuid)
);

create table if not exists t_trading_style_history
(
    history_id    bigint auto_increment
        primary key,
    change_reason varchar(500)                                                                         null,
    created_time  datetime(6)                                                                          null,
    new_style     enum ('C1_CONSERVATIVE', 'C2_CAUTIOUS', 'C3_MODERATE', 'C4_ACTIVE', 'C5_AGGRESSIVE') not null,
    old_style     enum ('C1_CONSERVATIVE', 'C2_CAUTIOUS', 'C3_MODERATE', 'C4_ACTIVE', 'C5_AGGRESSIVE') not null,
    operator_info varchar(200)                                                                         null
);
