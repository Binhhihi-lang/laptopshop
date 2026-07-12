// Toàn bộ lời gọi API dùng cho khu vực Admin.
const API_BASE = '/api/v1';

/**
 * Gọi API và tự parse JSON, tự ném lỗi kèm message rõ ràng khi request thất bại.
 */
async function apiRequest(path, options = {}) {
    // 1. Gọi dữ liệu từ Server
    const responseApi = await fetch(`${API_BASE}${path}`, options);

    // 2. Xử lý trường hợp Xóa thành công (HTTP 204 No Content - Không có body JSON)
    if (responseApi.status === 204) return null;

    // 3. Đọc dữ liệu JSON từ Server trả về ApiResponse chuẩn hóa (có code, message, result)
    let dataJson = null;
    const contentType = responseApi.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
        dataJson = await responseApi.json();
    }

    // 4. Nếu Server trả về mã lỗi HTTP (4xx, 5xx) hoặc mã code nghiệp vụ khác 1000
    if (!responseApi.ok || (dataJson && dataJson.code && dataJson.code !== 1000)) {

        // Ưu tiên lấy câu message báo lỗi nghiệp vụ từ Backend (ví dụ: "Không tìm thấy mã giảm giá")
        const message = (dataJson && dataJson.message) || `Đã có lỗi xảy ra (mã ${responseApi.status})`;
        throw new Error(message); // Ném lỗi để file HTML hứng và hiện showToast
    }

    // Nếu Backend dùng ApiResponse chuẩn hóa (có trường result)
    // thì trả thẳng dataJson.result ra ngoài. Nếu không thì trả về dataJson thuần.
    if (dataJson && dataJson.result !== undefined) {
        return dataJson.result;
    }

    return dataJson;
}

const UserAPI = {
  getAll: () => apiRequest('/users'),
  getById: (id) => apiRequest(`/users/${id}`),
  create: (formData) => apiRequest('/users', { method: 'POST', body: formData }),
  update: (id, formData) => apiRequest(`/users/${id}`, { method: 'PUT', body: formData }),
  remove: (id) => apiRequest(`/users/${id}`, { method: 'DELETE' }),
};

const RoleAPI = {
  getAll: () => apiRequest('/roles'),
};

const CategoryAPI = {
  getAll: () => apiRequest('/categories'),
  getById: (id) => apiRequest(`/categories/${id}`),
  create: (formData) => apiRequest('/categories', { method: 'POST', body: formData }),
  update: (id, formData) => apiRequest(`/categories/${id}`, { method: 'PUT', body: formData }),
  remove: (id) => apiRequest(`/categories/${id}`, { method: 'DELETE' }),
};

const ProductAPI = {
  getAll: () => apiRequest('/products'),
  getById: (id) => apiRequest(`/products/${id}`),
  create: (formData) => apiRequest('/products', { method: 'POST', body: formData }),
  update: (id, formData) => apiRequest(`/products/${id}`, { method: 'PUT', body: formData }),
  remove: (id) => apiRequest(`/products/${id}`, { method: 'DELETE' }),
};

// FormData (Multipart) sinh ra là để giải quyết các form dữ liệu có đính kèm file vật lý (như ảnh sản phẩm, file tài liệu, avatar).

const CouponAPI = {
  getAll: () => apiRequest('/coupons'),
  getById: (id) => apiRequest(`/coupons/${id}`),
  create: (payload) => apiRequest('/coupons', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }),
  update: (id, payload) => apiRequest(`/coupons/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }),
  remove: (id) => apiRequest(`/coupons/${id}`, { method: 'DELETE' }),
};

// Định dạng yyyy-MM-dd -> dd/MM/yyyy, dùng chung cho coupon
function formatDate(isoDate) {
  if (!isoDate) return 'Không giới hạn';
  const [y, m, d] = isoDate.split('-');
  return `${d}/${m}/${y}`;
}

// Hiển thị hình thức giảm giá của coupon: theo % hoặc theo số tiền cố định.
// Coupon chỉ có đúng 1 trong 2 trường discountPercent/discountAmount khác null.
function formatDiscount(coupon) {
  if (coupon.discountAmount != null) return formatCurrency(coupon.discountAmount);
  if (coupon.discountPercent != null) return coupon.discountPercent + '%';
  return '—';
}

// Các hàm tiện ích (helper functions) để hiển thị hình ảnh người dùng, sản phẩm, danh mục
// Khớp với WebMvcConfig: /images-upload/** -> file:///{upload.directory}
function avatarUrl(user) {
  if (user.avatar) return `/images-upload/avatar/${user.avatar}`;
  return `https://ui-avatars.com/api/?background=17B890&color=fff&name=${encodeURIComponent(user.fullName || user.email)}`;
}

function productImageUrl(product) {
  if (product.image) return `/images-upload/product/${product.image}`;
  return `https://ui-avatars.com/api/?background=6B7280&color=fff&name=${encodeURIComponent(product.name || 'SP')}`;
}

function categoryImageUrl(category) {
  if (category.image) return `/images-upload/category/${category.image}`;
  return `https://ui-avatars.com/api/?background=2F5FD6&color=fff&name=${encodeURIComponent(category.name || 'DM')}`;
}

// Định dạng số thành tiền VNĐ, vd 15000000 -> "15.000.000 đ"
function formatCurrency(value) {
  return new Intl.NumberFormat('vi-VN').format(value || 0) + ' đ';
}