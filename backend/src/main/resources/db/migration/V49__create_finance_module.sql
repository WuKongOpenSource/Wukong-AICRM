CREATE TABLE IF NOT EXISTS crm_finance_contract (
    contract_id BIGINT PRIMARY KEY,
    contract_no VARCHAR(100),
    contract_name VARCHAR(200) NOT NULL,
    customer_id BIGINT,
    project_id BIGINT,
    owner_id BIGINT,
    amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    sign_date DATE,
    start_date DATE,
    end_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    remark TEXT,
    source_type VARCHAR(50),
    source_text TEXT,
    ai_created BOOLEAN NOT NULL DEFAULT FALSE,
    del_flag INT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS crm_finance_receivable (
    receivable_id BIGINT PRIMARY KEY,
    contract_id BIGINT,
    customer_id BIGINT,
    project_id BIGINT,
    owner_id BIGINT,
    title VARCHAR(200) NOT NULL,
    amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    received_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    due_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    remark TEXT,
    source_type VARCHAR(50),
    source_text TEXT,
    ai_created BOOLEAN NOT NULL DEFAULT FALSE,
    del_flag INT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS crm_finance_payment (
    payment_id BIGINT PRIMARY KEY,
    receivable_id BIGINT,
    contract_id BIGINT,
    customer_id BIGINT,
    project_id BIGINT,
    owner_id BIGINT,
    amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(50),
    remark TEXT,
    source_type VARCHAR(50),
    source_text TEXT,
    ai_created BOOLEAN NOT NULL DEFAULT FALSE,
    del_flag INT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS crm_finance_invoice (
    invoice_id BIGINT PRIMARY KEY,
    contract_id BIGINT,
    receivable_id BIGINT,
    customer_id BIGINT,
    project_id BIGINT,
    owner_id BIGINT,
    invoice_no VARCHAR(100),
    title VARCHAR(200) NOT NULL,
    tax_no VARCHAR(100),
    amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    invoice_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'issued',
    remark TEXT,
    source_type VARCHAR(50),
    source_text TEXT,
    ai_created BOOLEAN NOT NULL DEFAULT FALSE,
    del_flag INT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS crm_finance_expense (
    expense_id BIGINT PRIMARY KEY,
    customer_id BIGINT,
    project_id BIGINT,
    owner_id BIGINT,
    expense_type VARCHAR(80) NOT NULL,
    amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    expense_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'recorded',
    remark TEXT,
    source_type VARCHAR(50),
    source_text TEXT,
    ai_created BOOLEAN NOT NULL DEFAULT FALSE,
    del_flag INT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_finance_contract_customer ON crm_finance_contract(customer_id);
CREATE INDEX IF NOT EXISTS idx_finance_contract_project ON crm_finance_contract(project_id);
CREATE INDEX IF NOT EXISTS idx_finance_receivable_due ON crm_finance_receivable(due_date, status);
CREATE INDEX IF NOT EXISTS idx_finance_receivable_customer ON crm_finance_receivable(customer_id);
CREATE INDEX IF NOT EXISTS idx_finance_payment_date ON crm_finance_payment(payment_date);
CREATE INDEX IF NOT EXISTS idx_finance_invoice_date ON crm_finance_invoice(invoice_date);
CREATE INDEX IF NOT EXISTS idx_finance_expense_date ON crm_finance_expense(expense_date);

INSERT INTO manager_menu (menu_id, parent_id, realm, realm_name, type) VALUES
    (2700, 0, 'finance', '财务管理', 3),
    (2701, 2700, 'finance:view', '查看', 5),
    (2702, 2700, 'finance:create', '新建', 5),
    (2703, 2700, 'finance:edit', '编辑', 5),
    (2704, 2700, 'finance:delete', '删除', 5),
    (2705, 2700, 'finance:export', '导出', 5),
    (2706, 2700, 'finance:ai_write', 'AI写入', 5)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO manager_role_menu (id, role_id, menu_id, data_scope, create_user_id, create_time)
SELECT 2700000000000 + r.role_id + m.menu_id,
       r.role_id,
       m.menu_id,
       5,
       1,
       CURRENT_TIMESTAMP
FROM manager_role r
JOIN manager_menu m ON m.menu_id BETWEEN 2700 AND 2706
WHERE r.realm = 'super_admin'
  AND NOT EXISTS (
      SELECT 1
      FROM manager_role_menu rm
      WHERE rm.role_id = r.role_id
        AND rm.menu_id = m.menu_id
  )
ON CONFLICT (id) DO NOTHING;
