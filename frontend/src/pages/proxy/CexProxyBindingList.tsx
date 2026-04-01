import React, {useEffect, useState} from 'react';
import {Button, Form, Input, message, Modal, Popconfirm, Select, Space, Table, Tag, Typography} from 'antd';
import {DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons';
import {cexProxyBindingService} from '../../services/cexProxyBindingService';
import {proxyService} from '../../services/proxyService';
import {CexProxyBinding} from '../../types/cexProxyBinding';
import {ProxyServiceConfig} from '../../types/proxy';

const {Title} = Typography;
const {Option} = Select;

const CEX_OPTIONS = ['okx', 'binance', 'bybit'];

const CexProxyBindingList: React.FC = () => {
    const [data, setData] = useState<CexProxyBinding[]>([]);
    const [proxies, setProxies] = useState<ProxyServiceConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editing, setEditing] = useState<CexProxyBinding | null>(null);
    const [form] = Form.useForm();

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const [bindings, activeProxies] = await Promise.all([
                cexProxyBindingService.getAll(),
                proxyService.getActiveConfigs()
            ]);
            setData(bindings);
            setProxies(activeProxies);
        } catch (e) {
            message.error('加载交易所代理绑定失败');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = () => {
        setEditing(null);
        form.resetFields();
        form.setFieldsValue({status: 'active'});
        setModalVisible(true);
    };

    const handleEdit = (row: CexProxyBinding) => {
        setEditing(row);
        form.setFieldsValue({
            cexName: row.cexName,
            proxyId: row.proxyId,
            status: row.status,
            description: row.description
        });
        setModalVisible(true);
    };

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            if (editing?.bindingId) {
                await cexProxyBindingService.update(editing.bindingId, values);
                message.success('更新成功');
            } else {
                await cexProxyBindingService.create(values);
                message.success('创建成功');
            }
            setModalVisible(false);
            await loadData();
        } catch (e) {
            message.error('保存失败');
        }
    };

    const handleDelete = async (id?: number) => {
        if (!id) {
            return;
        }
        try {
            await cexProxyBindingService.delete(id);
            message.success('删除成功');
            await loadData();
        } catch (e) {
            message.error('删除失败');
        }
    };

    return (
        <div style={{padding: 24}}>
            <Space style={{marginBottom: 16, width: '100%', justifyContent: 'space-between'}}>
                <Title level={4} style={{margin: 0}}>交易所代理绑定</Title>
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>
                    新增绑定
                </Button>
            </Space>
            <Table
                rowKey="bindingId"
                loading={loading}
                dataSource={data}
                columns={[
                    {title: '交易所', dataIndex: 'cexName', key: 'cexName'},
                    {title: '代理名称', dataIndex: 'proxyName', key: 'proxyName'},
                    {title: '代理地址', key: 'proxyAddr', render: (_, r) => `${r.serverHost}:${r.serverPort}`},
                    {
                        title: '状态', dataIndex: 'status', key: 'status', render: (status) =>
                            status === 'active' ? <Tag color="green">启用</Tag> : <Tag color="default">禁用</Tag>
                    },
                    {title: '描述', dataIndex: 'description', key: 'description'},
                    {
                        title: '操作',
                        key: 'action',
                        render: (_, row) => (
                            <Space>
                                <Button size="small" icon={<EditOutlined/>} onClick={() => handleEdit(row)}>编辑</Button>
                                <Popconfirm title="确认删除该绑定？" onConfirm={() => handleDelete(row.bindingId)}>
                                    <Button danger size="small" icon={<DeleteOutlined/>}>删除</Button>
                                </Popconfirm>
                            </Space>
                        )
                    }
                ]}
            />

            <Modal
                title={editing ? '编辑绑定' : '新增绑定'}
                open={modalVisible}
                onOk={handleSubmit}
                onCancel={() => setModalVisible(false)}
                destroyOnClose
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="cexName" label="交易所" rules={[{required: true, message: '请选择交易所'}]}>
                        <Select placeholder="请选择交易所">
                            {CEX_OPTIONS.map(item => <Option key={item} value={item}>{item}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item name="proxyId" label="代理服务器" rules={[{required: true, message: '请选择代理服务器'}]}>
                        <Select placeholder="请选择代理服务器">
                            {proxies.map(proxy => (
                                <Option key={proxy.proxyId} value={proxy.proxyId}>
                                    {proxy.proxyName} ({proxy.serverHost}:{proxy.serverPort})
                                </Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item name="status" label="状态" rules={[{required: true}]}>
                        <Select>
                            <Option value="active">active</Option>
                            <Option value="inactive">inactive</Option>
                        </Select>
                    </Form.Item>
                    <Form.Item name="description" label="描述">
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default CexProxyBindingList;
