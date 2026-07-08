// Toàn bộ lời gọi API dùng cho khu vực Admin.
// Sau này thêm ProductAPI, OrderAPI... thì viết tiếp vào file này.
const API_BASE = '/api/v1';

/**
 * Gọi API và tự parse JSON, tự ném lỗi kèm message rõ ràng khi request thất bại.
 */
async function apiRequest(path, options = {}) {
  // xử lý bất động bộ gọi dữ liệu
  const response = await fetch(`${API_BASE}${path}`, options);

  if (response.status === 204) return null; // No Content (vd sau khi xóa)

  // Lấy content-type để biết response trả về là JSON hay không
  const contentType = response.headers.get('content-type') || '';

  // Nếu content-type là JSON thì parse ra object, còn không thì trả về null
  // await response.json() để biến chuỗi chữ thành Object Javascript.
  const data = contentType.includes('application/json') ? await response.json() : null;

  // 
  if (!response.ok) {
    const message = (data && (data.message || data.error)) || `Đã có lỗi xảy ra (mã ${response.status})`;
    throw new Error(message);
  }
  return data;
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