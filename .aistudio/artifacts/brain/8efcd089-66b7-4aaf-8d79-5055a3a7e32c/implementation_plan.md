# Arsitektur Hybrid REST API & Prioritas Jaringan Lokal (Failover Cloud)

Rencana implementasi sistem Hybrid untuk konfigurasi REST API MySQL `billingku.php`: nilai template URL & API Key ditanam langsung sebagai default bawaan aplikasi, dapat diperbarui secara fleksibel di menu Pengaturan dengan proteksi PIN Admin, dan menggunakan strategi sinkronisasi prioritas jaringan lokal dengan failover instan ke cloud publik.

## User Review & Critical Decisions

> [!IMPORTANT]
> Keputusan yang telah dikonfirmasi melalui klarifikasi:
> - **Penanaman Nilai Bawaan (Default)**: URL Publik, URL Lokal, dan API Key ditanam di kode sebagai template default bawaan sehingga aplikasi langsung memiliki konfigurasi yang valid sejak pertama kali dibuka.
> - **Fleksibilitas Pengaturan (Hybrid)**: Nilai yang ditanam tetap dapat diubah kapan saja melalui menu Pengaturan MySQL Server di dalam aplikasi tanpa perlu melakukan compile atau instal ulang APK.
> - **Proteksi Keamanan PIN Admin**: Modifikasi konfigurasi URL dan API Key di menu Pengaturan dikunci ketat dengan PIN Admin agar kasir atau teknisi tidak mengubah parameter koneksi server.
> - **Strategi Rute Koneksi**: Memprioritaskan koneksi jaringan lokal (LAN) dengan batas waktu respons cepat (timeout singkat). Apabila jaringan lokal merespons sukses, transaksi langsung diproses via LAN. Jika jaringan lokal gagal atau perangkat sedang berada di luar jangkauan WiFi kantor, sistem seketika melakukan *seamless failover* ke URL Cloud publik.
> - **Fitur Reset ke Default Tanam**: Disediakan tombol cepat "Kembalikan ke Nilai Default Tanam" di menu Pengaturan.

---

## 1. Overview & Core Concept

- **Apa yang Dibangun**: Sistem konfigurasi hybrid untuk REST API sync engine MySQL `billingku.php`. Menggabungkan kenyamanan konfigurasi bawaan yang sudah tertanam (*pre-embedded defaults*) dengan fleksibilitas kustomisasi di antarmuka pengguna Android, dilengkapi mekanisme failover berjenjang (*tiered priority fallback: Local LAN -> Public Cloud*).
- **Target Pengguna**:
  - **Kasir & Teknisi**: Menikmati kecepatan respons transaksi tinggi saat terhubung ke WiFi kantor (LAN), dan tetap dapat bertransaksi saat di lapangan tanpa harus mengubah setelan secara manual.
  - **Administrator / Owner**: Memiliki kontrol penuh atas URL endpoint dan kredensial API dengan keamanan terjamin melalui autentikasi PIN Admin.
- **Nilai Utama**: Nol konfigurasi awal yang membingungkan (*zero-friction first launch*), keamanan terjaga dari staf yang tidak berwenang, dan transaksi bebas gangguan di berbagai kondisi sinyal jaringan.

---

## 2. User Experience & Visual Design

### Alur Pengguna (User Flows)
1. **First Launch & State Awal**:
   - Aplikasi langsung menginisialisasi parameter koneksi dari default tanam:
     - URL Publik Default: `https://api.billingku.my.id/billingku.php`
     - URL Lokal Default: `http://192.168.1.100/billingku.php`
     - API Key Default: `billingku_secure_key_2026`
     - Mode Koneksi: `Prioritas Lokal (Auto Fallback Cloud)`
2. **Akses Menu Pengaturan**:
   - Tab *Database MySQL & REST API* di menu Pengaturan dilindungi autentikasi PIN Admin.
   - Jika pengguna belum terverifikasi sebagai Admin, dialog PIN Admin muncul secara elegan dengan feedback visual.
3. **Formulir Pengaturan Hybrid**:
   - Banner status menampilkan badge: `Mode: Hybrid (Tanam + Modifiable)`.
   - Bidang input URL Publik dan URL Lokal dengan validasi format otomatis (`http://` / `https://`).
   - Bidang input API Secret Key dengan opsi intip/sembunyikan (*toggle password visibility*).
   - Indikator badge di samping kolom: `Bawaan Tanam` atau `Dimodifikasi Manual`.
   - Tombol **"Reset ke Nilai Default Tanam"** dengan dialog konfirmasi aman.
4. **Pengujian Koneksi & Failover**:
   - Tombol **"Uji Jalur Prioritas (Lokal ➔ Cloud)"**: Menampilkan animasi pengujian dan hasil deteksi jalur aktif dengan latency (ms).
   - Dialog hasil uji menampilkan status kedua endpoint secara transparan (misal: *LAN terhubung (12ms) - Rute Utama Aktif* atau *LAN tidak terjangkau (Timeout) - Beralih ke Cloud Publik (85ms)*).

### Desain Visual & Gaya Material 3
- **Kartu Pengaturan Berwarna Lembut**: Aksen Teal (`#0F766E` dan `#CCFBF1`) untuk menandakan sinkronisasi data cloud yang aman dan aktif.
- **Badge Status Interaktif**: Badge hijau emerald untuk koneksi sukses, amber untuk fallback aktif, dan abu-abu netral untuk default bawaan.
- **Feedback Haptik & Visual**: Snackbar atau toast notifikasi yang informatif saat parameter berhasil disimpan atau di-reset.

---

## 3. Key Product Decisions & Trade-Offs

### Keputusan 1: Strategi Prioritas Bertingkat vs Balapan Paralel
- **Pendekatan yang Dipilih**: Prioritas Jaringan Lokal dengan Fallback ke Publik (*Sequential Tiered Fallback* dengan short probe timeout 1.5 detik pada LAN).
- **Alasan**: Saat kasir berada di kantor, koneksi LAN selalu lebih disukai karena tidak memakan kuota internet dan latensinya sangat rendah. Menguji LAN terlebih dahulu selama max 1.5 detik sangat cepat dan menghemat bandwidth server cloud.
- **Alternatif yang Dipertimbangkan**: Uji balapan paralel (*Parallel Race*). Meskipun sedikit lebih cepat saat probe pertama kali, cara ini membebani server publik dengan ping ganda pada setiap pengecekan.

### Keputusan 2: Penguncian Menu Konfigurasi dengan PIN Admin
- **Pendekatan yang Dipilih**: Wajib verifikasi PIN Admin sebelum masuk ke tab pengaturan database atau sebelum menyimpan perubahan URL/Key.
- **Alasan**: Mencegah kasir atau staf lapangan salah mengubah alamat IP atau token keamanan yang dapat menyebabkan sinkronisasi data tagihan terputus.

### Keputusan 3: Penyimpanan Kredensial di SharedPreferences / DataStore Terenkripsi
- **Pendekatan yang Dipilih**: Nilai awal dibaca dari konstanta internal (`DEFAULT_PUBLIC_URL`, `DEFAULT_LOCAL_URL`, `DEFAULT_API_KEY`). Jika admin melakukan perubahan di UI, nilai tersimpan di penyimpanan lokal aplikasi (`SharedPreferences`) yang diisolasi oleh sandboxing Android OS. Tombol reset cukup menghapus nilai override tersebut.

---

## 4. Technical Architecture & Data Strategy

### Diagram Arsitektur Sistem

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│                                                                        │
│   ┌──────────────────────┐              ┌──────────────────────────┐   │
│   │   MysqlSettingsTab   │              │  AdminPinPromptDialog    │   │
│   │  (Input URL & Key)   │◄────────────►│  (Proteksi Akses Admin)  │   │
│   └──────────┬───────────┘              └──────────────────────────┘   │
└──────────────┼─────────────────────────────────────────────────────────┘
               │ Mengirim aksi simpan / reset / uji koneksi
               ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          SettingsViewModel                             │
│       - Validasi URL & API Key                                         │
│       - State flow: mysqlPublicUrl, mysqlLocalUrl, mysqlApiKey         │
│       - Evaluasi apakah nilai saat ini = nilai default tanam           │
└──────────────┬─────────────────────────────────────────────────────────┘
               │
               ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          MysqlSyncManager                              │
│                                                                        │
│   Const Template:                                                      │
│   - DEFAULT_PUBLIC_URL: "https://api.billingku.my.id/billingku.php"    │
│   - DEFAULT_LOCAL_URL:  "http://192.168.1.100/billingku.php"           │
│   - DEFAULT_API_KEY:    "billingku_secure_key_2026"                    │
│   - DEFAULT_MODE:       "PRIORITY_LOCAL" (Lokal -> Cloud)              │
│                                                                        │
│   Penyimpanan Override (SharedPreferences):                            │
│   - Jika kosong: gunakan konstanta bawaan (Embedded)                   │
│   - Jika diisi user: gunakan nilai tersimpan                           │
│   - Jika di-reset: hapus preferensi & kembali ke konstanta bawaan      │
│                                                                        │
│   Algoritma Resolusi Endpoint (Prioritas Lokal):                       │
│   1. Cek Local URL (LAN) timeout 1500ms                                │
│      ├─► Sukses ──► Gunakan Local URL (Fast LAN Mode)                  │
│      └─► Gagal  ──► Failover ke Public URL (Cloud Fallback Mode)       │
└──────────────┬─────────────────────────────────────────────────────────┘
               │
       ┌───────┴───────────────────────┐
       ▼                               ▼
┌──────────────┐                ┌──────────────┐
│  Server LAN  │                │ Server Cloud │
│ (192.168.x)  │                │   (Publik)   │
└──────────────┘                └──────────────┘
```

### Data Model & State Representation
- **`MysqlConfigState`**:
  - `publicUrl: String` (nilai aktif)
  - `localUrl: String` (nilai aktif)
  - `apiKey: String` (nilai aktif)
  - `endpointMode: String` (`PRIORITY_LOCAL`, `AUTO`, `LOCAL`, `PUBLIC`)
  - `isCustomized: Boolean` (true jika berbeda dari default tanam)
  - `activeResolvedUrl: String` (URL hasil uji rute terkini)
  - `lastProbeSummary: String` (deskripsi hasil probe)

### Mapping Interaksi Komponen
1. **Tombol "Simpan Konfigurasi"**:
   - Memvalidasi format URL.
   - Menyimpan nilai ke `MysqlSyncManager`.
   - Menguji koneksi jalur aktif dan memperbarui badge status.
2. **Tombol "Reset ke Default Tanam"**:
   - Membuka dialog konfirmasi.
   - Mengembalikan input URL Publik, URL Lokal, dan API Key ke konstanta template bawaan.
   - Memberikan notifikasi toast sukses.
3. **Uji Koneksi Berjenjang**:
   - Menguji Local URL terlebih dahulu.
   - Jika berhasil, menampilkan indikator *Jaringan Lokal Aktif*.
   - Jika gagal, otomatis menguji Public URL dan menampilkan indikator *Fallback Cloud Aktif*.
