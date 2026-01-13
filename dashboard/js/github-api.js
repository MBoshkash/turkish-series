/**
 * GitHub API Helper
 * للتعامل مع GitHub REST API لقراءة وكتابة الملفات
 */

class GitHubAPI {
    constructor() {
        this.token = '';
        this.owner = '';
        this.repo = '';
        this.branch = 'main';
        this.baseUrl = 'https://api.github.com';
    }

    /**
     * تهيئة الـ API
     */
    init(token, owner, repo) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
    }

    /**
     * تحميل الإعدادات من localStorage
     */
    loadSettings() {
        const settings = localStorage.getItem('github_settings');
        if (settings) {
            const parsed = JSON.parse(settings);
            this.token = parsed.token || '';
            this.owner = parsed.owner || 'MBoshkash';
            this.repo = parsed.repo || 'turkish-series';
            return true;
        }
        return false;
    }

    /**
     * حفظ الإعدادات في localStorage
     */
    saveSettings(token, owner, repo) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        localStorage.setItem('github_settings', JSON.stringify({
            token, owner, repo
        }));
    }

    /**
     * التحقق من صلاحية الاتصال
     */
    async testConnection() {
        if (!this.token) {
            throw new Error('لم يتم تعيين GitHub Token');
        }

        const response = await fetch(`${this.baseUrl}/repos/${this.owner}/${this.repo}`, {
            headers: this.getHeaders()
        });

        if (!response.ok) {
            if (response.status === 401) {
                throw new Error('Token غير صالح');
            } else if (response.status === 404) {
                throw new Error('Repository غير موجود');
            }
            throw new Error(`خطأ: ${response.status}`);
        }

        return await response.json();
    }

    /**
     * قراءة ملف من الـ repo
     */
    async readFile(path) {
        const url = `${this.baseUrl}/repos/${this.owner}/${this.repo}/contents/${path}`;

        const response = await fetch(url, {
            headers: this.getHeaders()
        });

        if (!response.ok) {
            if (response.status === 404) {
                return null;
            }
            throw new Error(`فشل قراءة الملف: ${response.status}`);
        }

        const data = await response.json();

        // فك ترميز Base64 مع دعم UTF-8
        const content = this.decodeBase64UTF8(data.content);
        return {
            content: content,
            sha: data.sha,
            path: data.path
        };
    }

    /**
     * فك ترميز Base64 مع دعم UTF-8 (للنصوص العربية)
     */
    decodeBase64UTF8(base64) {
        // إزالة الأسطر الجديدة من Base64
        const cleanBase64 = base64.replace(/\n/g, '');

        // تحويل Base64 إلى binary
        const binaryString = atob(cleanBase64);

        // تحويل إلى Uint8Array
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }

        // فك الترميز كـ UTF-8
        const decoder = new TextDecoder('utf-8');
        return decoder.decode(bytes);
    }

    /**
     * قراءة ملف JSON
     */
    async readJSON(path) {
        const file = await this.readFile(path);
        if (!file) return null;
        return {
            data: JSON.parse(file.content),
            sha: file.sha
        };
    }

    /**
     * كتابة ملف في الـ repo
     */
    async writeFile(path, content, message, sha = null) {
        const url = `${this.baseUrl}/repos/${this.owner}/${this.repo}/contents/${path}`;

        const body = {
            message: message,
            content: btoa(unescape(encodeURIComponent(content))), // ترميز UTF-8 ثم Base64
            branch: this.branch
        };

        // لو بنعدل ملف موجود، لازم نبعت الـ SHA
        if (sha) {
            body.sha = sha;
        }

        const response = await fetch(url, {
            method: 'PUT',
            headers: this.getHeaders(),
            body: JSON.stringify(body)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || `فشل الكتابة: ${response.status}`);
        }

        return await response.json();
    }

    /**
     * كتابة ملف JSON
     */
    async writeJSON(path, data, message, sha = null) {
        const content = JSON.stringify(data, null, 2);
        return await this.writeFile(path, content, message, sha);
    }

    /**
     * حذف ملف
     */
    async deleteFile(path, message, sha) {
        const url = `${this.baseUrl}/repos/${this.owner}/${this.repo}/contents/${path}`;

        const response = await fetch(url, {
            method: 'DELETE',
            headers: this.getHeaders(),
            body: JSON.stringify({
                message: message,
                sha: sha,
                branch: this.branch
            })
        });

        if (!response.ok) {
            throw new Error(`فشل الحذف: ${response.status}`);
        }

        return true;
    }

    /**
     * قراءة محتويات مجلد
     */
    async listDirectory(path) {
        const url = `${this.baseUrl}/repos/${this.owner}/${this.repo}/contents/${path}`;

        const response = await fetch(url, {
            headers: this.getHeaders()
        });

        if (!response.ok) {
            if (response.status === 404) {
                return [];
            }
            throw new Error(`فشل قراءة المجلد: ${response.status}`);
        }

        return await response.json();
    }

    /**
     * جلب Headers المطلوبة
     */
    getHeaders() {
        const headers = {
            'Accept': 'application/vnd.github.v3+json',
            'Content-Type': 'application/json'
        };

        if (this.token) {
            headers['Authorization'] = `token ${this.token}`;
        }

        return headers;
    }

    /**
     * التحقق من وجود Token
     */
    hasToken() {
        return !!this.token;
    }
}

// إنشاء instance واحد
const githubAPI = new GitHubAPI();

// تصدير للاستخدام
window.githubAPI = githubAPI;
