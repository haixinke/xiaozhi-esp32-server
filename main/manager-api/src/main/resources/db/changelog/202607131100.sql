-- 微信小程序用户绑定表：设置默认头像
UPDATE ai_wechat_user
SET avatar_url = 'https://oss.eggbabe.com/default-avatar/user/user-avatar.png'
WHERE avatar_url IS NULL OR avatar_url = '';
