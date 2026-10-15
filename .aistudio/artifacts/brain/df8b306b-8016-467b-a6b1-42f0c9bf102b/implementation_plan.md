# Saran & Rencana Perbaikan Desain UI Modern di Seluruh Menu Billingku

Rencana komprehensif perbaikan antarmuka visual (UI) dan tipografi modern untuk seluruh menu aplikasi **Billingku**, berfokus pada desain kartu visual yang elegan (*modern card elevation*), hierarki tipografi yang tajam, konsistensi palet warna Slate/Navy/Royal Blue, dan kemudahan navigasi kasir.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Arah Desain yang Dikonfirmasi Pengguna:**
> - **Fokus Utama:** Tampilan modern dengan kartu visual (*elevated modern cards*), palet warna elegan, dan tipografi yang lebih rapi serta mudah dibaca.
> - **Cakupan Prioritas:** Menyeluruh pada setiap menu aplikasi (Dashboard, Kasir, Pelanggan, Riwayat Transaksi, Settlement Kasir, dan Pengaturan).

---

## 1. Overview & Konsep Desain

Aplikasi **Billingku** adalah sistem POS dan manajemen penagihan internet RT/RW Net. Pengguna aplikasi ini sering mengoperasikannya dalam mobilitas tinggi (baik di meja kasir maupun di lapangan saat menagih ke rumah warga). 

Tujuan peningkatan UI ini adalah:
1. **Reduksi Kepadatan Visual (*Visual Clutter*):** Memberikan ruang napas (*breathing room/padding*) 12–16dp yang konsisten antar elemen.
2. **Hierarki Tipografi yang Kuat:** Membedakan dengan tegas antara angka nominal uang (besar & tebal), nama pelanggan/label (semi-bold), dan metadata pelengkap (ukuran kompak dengan warna netral terukur).
3. **Pemberian Identitas Visual Unik per Status:** Status lunas, tunggakan, dan isolir memiliki kode warna visual (*semantic badge*) yang langsung terbaca dalam 1 detik.

---

## 2. Saran & Rencana Desain pada Setiap Menu

### A. Menu Dashboard (Beranda & Ringkasan KPI)
* **Hero KPI Gradient Card:**
  - Mengubah ringkasan keuangan menjadi kartu modern bernuansa *Deep Midnight Navy & Royal Blue Accent* dengan bayangan lembut (*subtle ambient shadow*).
  - Menampilkan angka pendapatan bulan ini, estimasi tagihan tersisa, dan rasio pelanggan lunas dengan progress bar melengkung (*rounded smooth progress indicator*).
* **Quick Action Grid:**
  - Tombol aksi cepat (POS Kasir, Tambah Pelanggan, Hubungkan Printer, Sinkronisasi Server) disusun dalam grid 2x2 atau 4 kolom horizontal dengan sudut membulat 16dp dan ikon kontras tinggi.
* **Status Jaringan & Pengumuman:**
  - Kartu notifikasi gangguan (*Net-pay Broadcast*) dengan aksen amber/warning yang bersih, dilengkapi tombol dismiss/kelola.

### B. Menu Kasir & Tagihan (POS & Pembayaran)
* **Pill Filter Periode & Jatuh Tempo:**
  - Filter horizontal scroll (Semua, Tunggakan Lampau, Tagihan Bulan Ini, Jatuh Tempo Hari Ini) dibuat berbentuk *Pill Chips* dengan warna aktif *Blue Primary* dan badge angka kecil (*counter badge*).
* **Kartu Tagihan Pelanggan (Bill Cards):**
  - Pemisahan visual tegas antara tagihan bulan ini dan tunggakan lampau dengan aksen border tipis (*danger accent border* untuk tunggakan).
  - Penempatan nominal tagihan di sisi kanan dengan font tebal berukuran 16sp berwarna *Blue Accent*.
  - Label ODP dan Paket internet diletakkan dalam *micro-tag container* berwarna netral agar tidak membingungkan mata.
* **Modal Checkout & Swipe to Pay:**
  - Desain modal konfirmasi pembayaran menyerupai slip kasir digital dengan rincian biaya admin, potongan diskon, dan total akhir yang sangat jelas.
  - Mempertahankan slider *Swipe to Confirm* yang responsif dengan haptic feedback saat berhasil digeser.

### C. Menu Kelola Pelanggan (Database & ODP)
* **Sticky Search & Filter ODP:**
  - Kolom pencarian dengan sudut 14dp terintegrasi tombol filter cepat berdasarkan ODP atau Paket WiFi.
* **Kartu Pelanggan Modern:**
  - Avatar lingkaran inisial nama dengan warna latar belakang dinamis (*pastel tone*) untuk mempermudah identifikasi visual.
  - Status koneksi: Titik hijau lembut (*Pulse dot*) untuk "ACTIVE" dan badge merah tajam dengan ikon gembok untuk "ISOLATED".
  - Tombol aksi cepat: Ikon WhatsApp hijau dan ikon edit di sudut kanan kartu untuk akses cepat tanpa harus membuka detail.

### D. Menu Riwayat Transaksi (Log & Bukti Pembayaran)
* **Pengelompokan Berdasarkan Waktu:**
  - Pengelompokan daftar riwayat menggunakan header tanggal (*Hari Ini*, *Kemarin*, *Pekan Ini*) dengan gaya tipografi kapsul.
* **Kartu Struk Transaksi Mini:**
  - Tampilan nomor faktur, nama kasir, jam transaksi, dan badge metode pembayaran (*TUNAI* berwarna biru muda, *TRANSFER* berwarna ungu muda).
  - Tombol aksi cepat di setiap kartu: **Cetak Ulang** (ikon printer) dan **Kirim Struk WA** (ikon WhatsApp) langsung dari daftar.

### E. Menu Settlement & Tutup Buku Kasir
* **Shift Rekap Card:**
  - Kartu ringkasan penutupan shift kasir dengan visualisasi pembagian penerimaan uang kas fisik (*Cash*) vs penerimaan transfer/QRIS.
* **Audit Selisih Kas:**
  - Komponen input uang fisik yang secara otomatis membandingkan dengan total sistem, menampilkan status "Sesuai (Match)", "Lebih (Surplus)", atau "Kurang (Defisit)" dengan warna indikator yang intuitif.

### F. Menu Pengaturan & Profil Usaha
* **Categorized Settings Sections:**
  - Pengelompokan pengaturan ke dalam kartu bersudut 16dp: *Perangkat & Printer*, *Integrasi MySQL & Server*, *Staf & Keamanan PIN*, *Profil RT/RW Net*.
* **Live Bluetooth Printer Status Card:**
  - Kartu khusus di bagian atas yang menampilkan printer yang sedang terhubung dengan indikator sinyal hijau aktif dan tombol uji cetak (*Test Print*).

---

## 3. Sistem Desain & Tipografi (Design System Token)

```
┌─────────────────────────────────────────────────────────────┐
│                      Design System                          │
├─────────────────┬───────────────────────────────────────────┤
│ Background      │ Slate 50 (#F8FAFC)                        │
│ Surface Cards   │ Pure White (#FFFFFF) with 1.dp border     │
│ Primary Brand   │ Royal Blue (#1D4ED8 & #2563EB)            │
│ Dark Accent     │ Midnight Navy (#0F172A)                   │
│ Success Accent  │ Emerald Green (#059669)                   │
│ Warning Accent  │ Warm Amber (#D97706)                      │
│ Danger Accent   │ Crimson Red (#DC2626)                     │
│ Card Radius     │ 14.dp - 18.dp                             │
│ Spacing Grid    │ 8.dp / 12.dp / 16.dp standard             │
└─────────────────┴───────────────────────────────────────────┘
```

---

## 4. Arsitektur Komponen UI

```
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity / App Scaffold              │
├─────────────────────────────────────────────────────────────┤
│  TopAppBar: Clean Header + Profile/Staff Badge              │
├─────────────────────────────────────────────────────────────┤
│  Screen Content (LazyColumn / Spaced Layout):               │
│  ├── [KPI / Summary Hero Cards]                             │
│  ├── [Sticky Search & Filter Chips]                         │
│  ├── [Elevated Item Cards with Semantic Badges]             │
│  └── [Bottom Action Floating / Pinned Sheet]                │
├─────────────────────────────────────────────────────────────┤
│  BottomNavigationBar: 5 Menu Navigasi dengan Active Pill    │
└─────────────────────────────────────────────────────────────┘
```

### Langkah Selanjutnya Setelah Persetujuan:
Setelah Anda menyetujui rencana desain ini dengan menekan **Proceed**, kita dapat mulai menerapkan pembaruan visual modern ini ke menu-menu aplikasi secara bertahap dan teratur tanpa mengubah logika bisnis atau database yang sudah berjalan.
