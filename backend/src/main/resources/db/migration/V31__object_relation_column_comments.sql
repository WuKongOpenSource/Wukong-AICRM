-- 通讯录与关系模块字段备注

COMMENT ON COLUMN manager_user.employee_status IS '员工状态：active=在职，resigned=离职，disabled=停用';
COMMENT ON COLUMN crm_chat_session.employee_id IS '员工对象会话关联的员工ID';
COMMENT ON COLUMN crm_knowledge.employee_id IS '员工对象附件/知识库关联的员工ID';

COMMENT ON TABLE crm_relation IS '关系人/外部联系人对象表';
COMMENT ON COLUMN crm_relation.relation_id IS '关系人ID';
COMMENT ON COLUMN crm_relation.name IS '关系人姓名';
COMMENT ON COLUMN crm_relation.avatar IS '头像文件路径或对象存储Key';
COMMENT ON COLUMN crm_relation.phone IS '手机号或联系电话';
COMMENT ON COLUMN crm_relation.wechat IS '微信号';
COMMENT ON COLUMN crm_relation.email IS '邮箱';
COMMENT ON COLUMN crm_relation.relation_type IS '关系类型，默认 other';
COMMENT ON COLUMN crm_relation.company IS '所属公司或组织';
COMMENT ON COLUMN crm_relation.remark IS '备注';
COMMENT ON COLUMN crm_relation.source IS '来源：manual=手动创建，customer/contact 等表示从业务对象转换或关联生成';
COMMENT ON COLUMN crm_relation.source_customer_id IS '来源客户ID';
COMMENT ON COLUMN crm_relation.source_contact_id IS '来源联系人ID';
COMMENT ON COLUMN crm_relation.status IS '状态：1=正常，0=停用/删除';
COMMENT ON COLUMN crm_relation.create_user_id IS '创建人用户ID';
COMMENT ON COLUMN crm_relation.update_user_id IS '最后更新人用户ID';
COMMENT ON COLUMN crm_relation.create_time IS '创建时间';
COMMENT ON COLUMN crm_relation.update_time IS '更新时间';
COMMENT ON COLUMN crm_relation.tenant_id IS '租户ID';

COMMENT ON COLUMN crm_task.relation_id IS '关联关系人ID';
COMMENT ON COLUMN crm_schedule.relation_id IS '关联关系人ID';
COMMENT ON COLUMN crm_follow_up.relation_id IS '关联关系人ID';
COMMENT ON COLUMN crm_knowledge.relation_id IS '关联关系人ID';
COMMENT ON COLUMN crm_chat_session.relation_id IS '关系人对象会话关联的关系人ID';
