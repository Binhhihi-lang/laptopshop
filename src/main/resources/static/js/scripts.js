// Các hiệu ứng UI dùng chung cho toàn site (không liên quan tới gọi API)

/**
 * Fetch 1 file HTML và nhúng nội dung vào phần tử match với targetSelector.
 */
async function loadPartial(targetSelector, url) {
  const el = document.querySelector(targetSelector);
  if (!el) return;
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Không tải được ${url}`);
    el.innerHTML = await res.text();
  } catch (err) {
    console.error(err);
  }
}

/**
 * Nhúng sidebar + header dùng chung cho các trang admin, đánh dấu active menu,
 * và gắn sự kiện toggle sidebar (mobile).
 * Gọi ở cuối mỗi trang admin: initAdminLayout('user');
 */
function initAdminLayout(activePage) {
  const token = localStorage.getItem('accessToken');

  Promise.all([
    loadPartial('#sidebar-placeholder', '/admin/layout/sidebar.html'),
    loadPartial('#header-placeholder', '/admin/layout/header.html'),
  ]).then(() => {
    // 1. Đánh dấu active menu
    document.querySelectorAll('.admin-sidebar .nav-link').forEach((link) => {
      if (link.dataset.page === activePage) link.classList.add('active');
    });

    // 2. Toggle sidebar mobile
    document.getElementById('sidebarToggle')?.addEventListener('click', () => {
      document.getElementById('adminSidebar')?.classList.toggle('open');
    });

    // hiển thị tên ADMIN
    try {
      const base64Url = token.split('.')[1]; 
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/'); 
      const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));

      const payloadData = JSON.parse(jsonPayload);
      const adminNameEl = document.getElementById("adminName");
      if (adminNameEl && payloadData.sub) {
          adminNameEl.innerText = payloadData.sub; // Set họ tên user vào thẻ <small id="adminName">
      }
    } catch (error) {
      console.error("Lỗi giải mã token hiển thị tên admin:", error);
    }


    // Đăng xuất 
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', (e) => {
            e.preventDefault(); 
            if (confirm('Bạn có chắc chắn muốn đăng xuất khỏi hệ thống quản trị?')) {
                logout(); // Gọi hàm logout() trong file admin-js của bạn
            }
        });
    }

  });
}
/**
 * Hiện toast thông báo góc phải màn hình. Cần có <div id="toast-container"> trong trang.
 */
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  if (!container) {
    alert(message);
    return;
  }
  const toastEl = document.createElement('div');
  toastEl.className = `toast align-items-center text-bg-${type} border-0`;
  toastEl.setAttribute('role', 'alert');
  toastEl.innerHTML = `
    <div class="d-flex">
      <div class="toast-body">${message}</div>
      <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
    </div>`;
  container.appendChild(toastEl);

  const toast = new bootstrap.Toast(toastEl, { delay: 3500 });
  toast.show();
  toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
}
