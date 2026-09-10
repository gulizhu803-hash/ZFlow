-- 仅补入代码固定引用的默认顾问；不创建账户、密钥、演示模型或定时任务。
-- maxMessages=20 为本次设计的初始值，可在管理后台调整。
SET NAMES utf8mb4;
USE `ai-agent`;

INSERT INTO ai_advisor (advisor_id, advisor_name, advisor_type, advisor_param)
SELECT 'advisor_work_memory', '工作流对话记忆', 'Memory', '{"maxMessages":20}'
WHERE NOT EXISTS (
    SELECT 1 FROM ai_advisor WHERE advisor_id = 'advisor_work_memory'
);
