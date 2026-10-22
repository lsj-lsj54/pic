/**
 * 我的空间页：空间列表、创建、重命名、删除、进入空间
 * <p>
 * 空间分为私有空间（spaceType=0）和团队空间（spaceType=1）：
 * - 每个用户最多只能创建一个私有空间和一个团队空间；
 * - 团队空间可以邀请其他成员协作，成员按角色获得权限；
 * - 我加入的团队空间单独列在下方，与「我创建的空间」区分开。
 */
import { spaceApi, spaceUserApi } from '../api.js';
import {
  $, $$, state, toast, escapeHtml, formatTime, renderPager, renderEmpty,
  confirmDialog, showModal, closeModal, getFormValues, spaceLevelInfo,
  spaceLevelOptions, spaceTypeInfo, spaceRoleInfo, renderSpaceUsage, normalizePage,
  renderSkeleton, revealOnScroll, attachRippleAll,
} from '../ui.js';
import { openSpaceMemberModal } from '../components/spaceMember.js';
import { navigate } from '../app.js';

/** 每页数量 */
const PAGE_SIZE = 12;

const query = {
  current: 1,
  pageSize: PAGE_SIZE,
  sortField: 'createTime',
  sortOrder: 'descend',
};

/** 拉取当前用户自己创建的空间 */
async function load(el) {
  const grid = $('#grid', el);
  grid.innerHTML = renderSkeleton(PAGE_SIZE);
  try {
    const data = await spaceApi.myListVOByPage({ ...query });
    renderGrid(el, normalizePage(data));
  } catch (e) {
    grid.innerHTML = renderEmpty(e.message);
  }
}

/** 渲染空间卡片 */
function renderGrid(el, page) {
  const records = page.records || [];
  const grid = $('#grid', el);
  const pager = $('#pager', el);

  if (records.length === 0) {
    grid.innerHTML = renderEmpty('还没有空间，点右上角「创建空间」开始整理你的图片');
    pager.innerHTML = '';
    return;
  }

  grid.innerHTML = records.map((item) => {
    const level = spaceLevelInfo(item.spaceLevel);
    const type = spaceTypeInfo(item.spaceType);
    const isTeam = Number(item.spaceType) === 1;
    return `
      <article class="space-card reveal" data-id="${item.id}" data-type="${item.spaceType || 0}">
        <div class="space-card-head">
          <h3 class="space-name" title="${escapeHtml(item.spaceName)}">${escapeHtml(item.spaceName)}</h3>
          <div class="space-tags">
            <span class="tag ${level.cls}">${level.text}</span>
            <span class="tag ${type.cls}">${type.text}</span>
          </div>
        </div>
        ${renderSpaceUsage(item)}
        <div class="space-card-meta">
          <span>创建于 ${formatTime(item.createTime)}</span>
        </div>
        <div class="card-actions">
          <button class="btn btn-sm btn-primary" data-action="open" type="button">进入空间</button>
          ${isTeam ? '<button class="btn btn-sm" data-action="members" type="button">成员</button>' : ''}
          <button class="btn btn-sm" data-action="edit" type="button">重命名</button>
          <button class="btn btn-sm btn-danger" data-action="delete" type="button">删除</button>
        </div>
      </article>`;
  }).join('');

  $$('[data-action]', grid).forEach((btn) => {
    const card = btn.closest('.space-card');
    const id = card.dataset.id;
    const space = records.find((item) => String(item.id) === String(id));
    btn.addEventListener('click', () => {
      const action = btn.dataset.action;
      if (action === 'open') {
        navigate('/space/detail', { id });
        return;
      }
      if (action === 'edit') {
        openEditModal(el, space);
        return;
      }
      if (action === 'members') {
        openSpaceMemberModal(space, { onChanged: () => load(el) });
        return;
      }
      if (action === 'delete') {
        removeSpace(el, space);
      }
    });
  });

  pager.innerHTML = renderPager({
    current: page.current,
    pageSize: page.size,
    total: page.total,
  });
  $$('[data-page]', pager).forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = Number(btn.dataset.page);
      if (target < 1) {
        return;
      }
      query.current = target;
      load(el);
    });
  });

  revealOnScroll(grid);
  attachRippleAll(grid);
}

/** 创建空间弹窗 */
function openCreateModal(el) {
  // 普通用户只能创建普通版空间，后端也会再校验一次
  const onlyCommon = !state.isAdmin();
  const modal = showModal({
    title: '创建空间',
    content: `
      <form id="space-form" class="form">
        <label class="field">
          <span>空间名称</span>
          <input name="spaceName" type="text" maxlength="30" required
                 placeholder="例如：我的旅行照片" autocomplete="off">
        </label>
        <label class="field">
          <span>空间类型</span>
          <select name="spaceType">
            <option value="0">私有空间（仅自己可见）</option>
            <option value="1">团队空间（可邀请成员协作）</option>
          </select>
        </label>
        <label class="field">
          <span>空间级别</span>
          <select name="spaceLevel">${spaceLevelOptions(0, onlyCommon)}</select>
        </label>
        <p class="form-tip" id="space-tip">
          每个用户最多创建一个私有空间和一个团队空间；普通用户只能创建普通版。
        </p>
        <div class="modal-foot">
          <button class="btn" type="button" data-modal-close>取消</button>
          <button class="btn btn-primary" type="submit">创建</button>
        </div>
      </form>`,
  });

  $('#space-form', modal).addEventListener('submit', async (e) => {
    e.preventDefault();
    const values = getFormValues(e.target);
    try {
      await spaceApi.add({
        spaceName: values.spaceName,
        spaceType: Number(values.spaceType) || 0,
        spaceLevel: Number(values.spaceLevel) || 0,
      });
      closeModal();
      toast('空间创建成功', 'success');
      query.current = 1;
      load(el);
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}

/** 重命名弹窗 */
function openEditModal(el, space) {
  const modal = showModal({
    title: '重命名空间',
    content: `
      <form id="space-edit-form" class="form">
        <label class="field">
          <span>空间名称</span>
          <input name="spaceName" type="text" maxlength="30" required
                 value="${escapeHtml(space.spaceName)}" autocomplete="off">
        </label>
        <div class="modal-foot">
          <button class="btn" type="button" data-modal-close>取消</button>
          <button class="btn btn-primary" type="submit">保存</button>
        </div>
      </form>`,
  });

  $('#space-edit-form', modal).addEventListener('submit', async (e) => {
    e.preventDefault();
    const values = getFormValues(e.target);
    try {
      await spaceApi.edit({ id: space.id, spaceName: values.spaceName });
      closeModal();
      toast('保存成功', 'success');
      load(el);
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}

/** 删除空间 */
async function removeSpace(el, space) {
  const confirmed = await confirmDialog(
    `删除空间「${space.spaceName}」会同时删除空间内的 ${Number(space.totalCount) || 0} 张图片，且无法恢复，确定继续吗？`,
    '删除空间',
  );
  if (!confirmed) {
    return;
  }
  try {
    await spaceApi.delete(space.id);
    toast('删除成功', 'success');
    load(el);
  } catch (e) {
    toast(e.message, 'error');
  }
}

/** 加载并渲染「我加入的团队空间」 */
async function loadJoined(el) {
  const container = $('#joined-section', el);
  if (!container) return;
  container.innerHTML = `<div class="loading">加载中…</div>`;
  try {
    const data = await spaceUserApi.listMy();
    renderJoined(container, data || []);
  } catch (e) {
    container.innerHTML = renderEmpty(e.message);
  }
}

/** 渲染我加入的团队空间 */
function renderJoined(container, list) {
  if (list.length === 0) {
    container.innerHTML = renderEmpty('还没有加入任何团队空间');
    return;
  }
  container.innerHTML = list.map((item) => {
    const space = item.space || {};
    const role = item.spaceRole ? spaceRoleInfo(item.spaceRole) : { text: '成员', cls: 'tag-viewer' };
    return `
      <article class="space-card reveal joined-card" data-id="${space.id}">
        <div class="space-card-head">
          <h3 class="space-name" title="${escapeHtml(space.spaceName || '')}">${escapeHtml(space.spaceName || '')}</h3>
          <span class="tag ${role.cls}">我的角色：${role.text}</span>
        </div>
        ${renderSpaceUsage(space)}
        <div class="space-card-meta">
          <span>创建于 ${formatTime(space.createTime)}</span>
        </div>
        <div class="card-actions">
          <button class="btn btn-sm btn-primary" data-action="open" type="button">进入空间</button>
          <button class="btn btn-sm btn-danger" data-action="quit" type="button">退出团队</button>
        </div>
      </article>`;
  }).join('');

  $$('[data-action]', container).forEach((btn) => {
    const card = btn.closest('.joined-card');
    const id = card.dataset.id;
    const item = list.find((i) => String(i.space?.id || i.spaceId) === String(id));
    btn.addEventListener('click', () => {
      const action = btn.dataset.action;
      if (action === 'open') {
        navigate('/space/detail', { id });
        return;
      }
      if (action === 'quit') {
        quitTeam(item, container);
      }
    });
  });

  revealOnScroll(container);
  attachRippleAll(container);
}

/** 退出团队 */
async function quitTeam(item, container) {
  if (!item || !item.id) {
    toast('缺少成员记录', 'error');
    return;
  }
  const confirmed = await confirmDialog('退出团队后将无法查看该空间的图片，确定继续吗？', '退出团队');
  if (!confirmed) return;
  try {
    await spaceUserApi.delete(item.id);
    toast('已退出团队', 'success');
    loadJoined(container.closest('#view-space'));
  } catch (e) {
    toast(e.message, 'error');
  }
}

export default {
  /**
   * 渲染我的空间页
   *
   * @param {HTMLElement} el 视图容器
   */
  render(el) {
    el.innerHTML = `
      <section class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title">我的空间</h2>
          <span class="page-sub">把图片按主题分空间管理，团队空间可邀请成员协作</span>
        </div>
        <div class="toolbar-right">
          <button class="btn btn-primary" id="create-space-btn" type="button">创建空间</button>
        </div>
      </section>
      <h3 class="section-title">我创建的空间</h3>
      <section id="grid" class="space-grid"></section>
      <div id="pager"></div>
      <h3 class="section-title">我加入的团队空间</h3>
      <section id="joined-section" class="space-grid"></section>`;

    $('#create-space-btn', el).addEventListener('click', () => openCreateModal(el));

    load(el);
    loadJoined(el);
  },
};
