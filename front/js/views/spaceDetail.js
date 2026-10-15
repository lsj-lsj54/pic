/**
 * 空间详情页：查看某个空间的信息与其中的图片，支持上传、编辑、重新上传、删除
 * <p>
 * 只有空间创建者（或管理员）能进入：后端在查询空间和空间内图片时都会再做一次校验。
 */
import { spaceApi, pictureApi } from '../api.js';
import {
  $, $$, toast, escapeHtml, formatTime, formatSize, renderPager, renderEmpty,
  confirmDialog, REVIEW_STATUS, spaceLevelInfo, renderSpaceUsage, normalizePage,
  renderSkeleton, revealOnScroll, bindImageFade, attachRippleAll, attachRipple,
} from '../ui.js';
import { openPictureDetail, openEditModal, openUploadModal, thumbUrl } from '../components/picture.js';
import { openLightbox } from '../components/lightbox.js';
import { navigate } from '../app.js';

/** 每页数量，后端限制不能超过 20 */
const PAGE_SIZE = 12;

/** 当前空间，render 时从路由参数写入 */
let currentSpace = null;

const query = {
  current: 1,
  pageSize: PAGE_SIZE,
  sortField: 'createTime',
  sortOrder: 'descend',
};

/** 渲染空间信息（名称、级别、用量） */
function renderSpaceInfo(el, space) {
  const info = $('#space-info', el);
  if (!info) {
    return;
  }
  const level = spaceLevelInfo(space.spaceLevel);
  info.innerHTML = `
    <div class="space-detail-head">
      <h2 class="page-title">${escapeHtml(space.spaceName)}</h2>
      <span class="tag ${level.cls}">${level.text}</span>
      <a class="btn btn-sm" href="#/analyze?spaceId=${space.id}">查看分析</a>
    </div>
    ${renderSpaceUsage(space)}`;
}

/** 重新加载空间信息：上传、删除后用量会变，必须刷新进度条 */
async function loadSpace(el) {
  try {
    currentSpace = await spaceApi.getVOById(currentSpace.id);
    renderSpaceInfo(el, currentSpace);
  } catch (e) {
    $('#space-info', el).innerHTML = renderEmpty(e.message);
  }
}

/** 加载空间内的图片 */
async function loadPictures(el) {
  const grid = $('#grid', el);
  grid.innerHTML = renderSkeleton(PAGE_SIZE);
  try {
    const data = await pictureApi.listVOByPage({ ...query, spaceId: currentSpace.id });
    renderGrid(el, normalizePage(data));
  } catch (e) {
    grid.innerHTML = renderEmpty(e.message);
  }
}

/** 上传成功或删除后：用量和列表都要刷新 */
function reload(el) {
  loadSpace(el);
  loadPictures(el);
}

/** 删除空间内的图片 */
async function removePicture(el, id) {
  const confirmed = await confirmDialog('删除后无法恢复，确定要删除这张图片吗？');
  if (!confirmed) {
    return;
  }
  try {
    await pictureApi.delete(id);
    toast('删除成功', 'success');
    reload(el);
  } catch (e) {
    toast(e.message, 'error');
  }
}

/** 渲染图片卡片 */
function renderGrid(el, page) {
  const records = page.records || [];
  const grid = $('#grid', el);
  const pager = $('#pager', el);

  if (records.length === 0) {
    grid.innerHTML = renderEmpty('这个空间还没有图片，点右上角「上传图片」放几张进来');
    pager.innerHTML = '';
    return;
  }

  grid.innerHTML = records.map((item) => {
    const review = REVIEW_STATUS[item.reviewStatus] || { text: '未知', cls: 'tag-pending' };
    return `
      <article class="card reveal" data-id="${item.id}">
        <div class="card-img">
          <img src="${escapeHtml(thumbUrl(item))}" alt="${escapeHtml(item.name)}" loading="lazy" data-fade>
          <span class="card-badge ${review.cls}">${review.text}</span>
        </div>
        <div class="card-body">
          <h3 class="card-title" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</h3>
          <p class="card-desc">${escapeHtml(item.introduction) || '暂无简介'}</p>
          <div class="card-meta">
            <span>${formatSize(item.picSize)}</span>
            <span>${formatTime(item.createTime)}</span>
          </div>
        </div>
        <div class="card-actions">
          <button class="btn btn-sm" data-action="detail" type="button">查看</button>
          <button class="btn btn-sm" data-action="edit" type="button">编辑</button>
          <button class="btn btn-sm" data-action="reupload" type="button">重传</button>
          <button class="btn btn-sm btn-danger" data-action="delete" type="button">删除</button>
        </div>
      </article>`;
  }).join('');

  $$('[data-action]', grid).forEach((btn) => {
    const id = btn.closest('.card').dataset.id;
    btn.addEventListener('click', async () => {
      const action = btn.dataset.action;
      if (action === 'detail') {
        openPictureDetail(id, { onChanged: () => reload(el) });
        return;
      }
      if (action === 'edit') {
        // 空间里的图片都是自己的，直接查看详情后编辑即可
        try {
          const picture = await pictureApi.getVOById(id);
          openEditModal(picture, { onSuccess: () => reload(el) });
        } catch (e) {
          toast(e.message, 'error');
        }
        return;
      }
      if (action === 'reupload') {
        // 带上 spaceId，覆盖上传后图片仍留在当前空间
        openUploadModal({
          pictureId: id,
          spaceId: currentSpace.id,
          spaceName: currentSpace.spaceName,
          onSuccess: () => reload(el),
        });
        return;
      }
      if (action === 'delete') {
        removePicture(el, id);
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
      loadPictures(el);
    });
  });

  // 点击缩略图全屏查看
  $$('.card-img', grid).forEach((imgBox) => {
    imgBox.addEventListener('click', () => {
      const id = imgBox.closest('.card').dataset.id;
      const index = records.findIndex((item) => String(item.id) === String(id));
      openLightbox({ pictures: records, index: index < 0 ? 0 : index });
    });
  });

  bindImageFade(grid);
  revealOnScroll(grid);
  attachRippleAll(grid);
  $$('[data-action]', grid).forEach(attachRipple);
}

export default {
  /**
   * 渲染空间详情页
   *
   * @param {HTMLElement} el     视图容器
   * @param {object}      params 路由参数，params.id 为空间 id
   */
  render(el, params = {}) {
    const spaceId = params.id;
    if (!spaceId) {
      el.innerHTML = `
        ${renderEmpty('缺少空间 id')}
        <div class="page-actions"><a class="btn btn-sm" href="#/space">返回我的空间</a></div>`;
      return;
    }
    currentSpace = { id: spaceId };
    query.current = 1;

    el.innerHTML = `
      <section class="toolbar">
        <div class="toolbar-left">
          <button class="btn btn-sm" id="back-btn" type="button">← 我的空间</button>
          <div id="space-info" class="space-info"><div class="loading">加载中…</div></div>
        </div>
        <div class="toolbar-right">
          <button class="btn btn-primary" id="upload-btn" type="button">上传图片</button>
        </div>
      </section>
      <section id="grid" class="grid"></section>
      <div id="pager"></div>`;

    $('#back-btn', el).addEventListener('click', () => navigate('/space'));
    $('#upload-btn', el).addEventListener('click', () => {
      openUploadModal({
        spaceId: currentSpace.id,
        spaceName: currentSpace.spaceName,
        onSuccess: () => reload(el),
      });
    });

    // 先拿空间信息再查图片：拿不到（无权限/已删除）时只提示一次，不再发多余的请求
    spaceApi.getVOById(spaceId)
      .then((space) => {
        currentSpace = space;
        renderSpaceInfo(el, space);
        loadPictures(el);
      })
      .catch((e) => {
        $('#space-info', el).innerHTML = renderEmpty(`${e.message}（可能是空间已被删除，或不属于你）`);
        $('#grid', el).innerHTML = '';
      });
  },
};
