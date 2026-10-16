/**
 * 登录 / 注册页
 */
import { userApi } from '../api.js';
import { $, state, getFormValues, toast, showModal, closeModal } from '../ui.js';
import { onLoginSuccess } from '../app.js';

/** 打开注册弹窗 */
function openRegisterModal() {
  const modal = showModal({
    title: '注册新账号',
    content: `
      <form id="register-form" class="form">
        <label class="field">
          <span>账号</span>
          <input name="userAccount" type="text" placeholder="至少 2 位" autocomplete="username" required>
        </label>
        <label class="field">
          <span>昵称</span>
          <input name="userName" type="text" maxlength="20" placeholder="展示给其他人看，最多 20 字（不填自动生成）">
        </label>
        <label class="field">
          <span>密码</span>
          <input name="userPassword" type="password" placeholder="至少 5 位" autocomplete="new-password" required>
        </label>
        <label class="field">
          <span>确认密码</span>
          <input name="checkPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" required>
        </label>
        <div class="modal-foot">
          <button class="btn" type="button" data-cancel>取消</button>
          <button class="btn btn-primary" type="submit">注册</button>
        </div>
      </form>`,
  });

  $('[data-cancel]', modal).addEventListener('click', closeModal);
  $('#register-form', modal).addEventListener('submit', async (e) => {
    e.preventDefault();
    const values = getFormValues(e.target);
    if (!values.userAccount || !values.userPassword) {
      toast('账号和密码不能为空', 'error');
      return;
    }
    // 与后端 UserConstant.MIN_PASSWORD_LENGTH 保持一致
    if (values.userPassword.length < 5) {
      toast('密码至少 5 位', 'error');
      return;
    }
    if (values.userPassword !== values.checkPassword) {
      toast('两次输入的密码不一致', 'error');
      return;
    }
    try {
      // userName 为空时后端会自动生成一个「用户 + 随机数」的昵称
      await userApi.register(values);
      closeModal();
      toast('注册成功，请登录', 'success');
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}

export default {
  /**
   * 渲染登录页
   *
   * @param {HTMLElement} el 视图容器
   */
  render(el) {
    el.innerHTML = `
      <div class="auth-page">
        <div class="auth-card">
          <div class="auth-logo"><span class="logo-mark">图</span></div>
          <h1>云图库</h1>
          <p class="auth-sub">登录后即可上传和管理你的图片</p>
          <form id="login-form" class="form">
            <label class="field">
              <span>账号</span>
              <input name="userAccount" type="text" placeholder="请输入账号" autocomplete="username" required>
            </label>
            <label class="field">
              <span>密码</span>
              <input name="userPassword" type="password" placeholder="请输入密码" autocomplete="current-password" required>
            </label>
            <button class="btn btn-primary btn-block" type="submit">登 录</button>
          </form>
          <div class="auth-foot">
            <span>还没有账号？</span>
            <button class="link-btn" id="to-register" type="button">立即注册</button>
            <a class="link-btn" href="#/">先去逛逛</a>
          </div>
        </div>
      </div>`;

    $('#to-register', el).addEventListener('click', openRegisterModal);
    $('#login-form', el).addEventListener('submit', async (e) => {
      e.preventDefault();
      const values = getFormValues(e.target);
      if (!values.userAccount || !values.userPassword) {
        toast('请输入账号和密码', 'error');
        return;
      }
      const submitBtn = $('button[type="submit"]', e.target);
      submitBtn.disabled = true;
      submitBtn.textContent = '登录中…';
      try {
        const user = await userApi.login(values);
        // 先把登录用户写回全局状态，onLoginSuccess 里的 isAdmin() 判断才准确
        state.setCurrentUser(user);
        toast(`欢迎回来，${user.userName || user.userAccount}`, 'success');
        await onLoginSuccess();
      } catch (err) {
        toast(err.message, 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = '登 录';
      }
    });
  },
};
