create table if not exists t_backtest_task
(
    id                  bigint auto_increment primary key,
    task_name           varchar(100) not null,
    freqtrade_config_id bigint       not null,
    strategy_config_id  bigint       not null,
    time_range          varchar(50)  null,
    timeframe           varchar(20)  null,
    status              varchar(20)  not null,
    error_msg           text         null,
    pid                 varchar(50)  null,
    created_at          datetime(6)  null,
    finished_at         datetime(6)  null
);

create table if not exists t_backtest_result
(
    id               bigint auto_increment primary key,
    task_id          bigint         not null,
    total_profit_abs decimal(18, 8) null,
    total_profit_pct decimal(10, 4) null,
    max_drawdown_abs decimal(18, 8) null,
    max_drawdown_pct decimal(10, 4) null,
    win_rate         decimal(10, 4) null,
    total_trades     int            null,
    result_json_path varchar(500)   null,
    constraint uk_backtest_result_task unique (task_id)
);
