# MotoParts USA - Android app (Part 2)

Kotlin + Jetpack Compose customer app. Talks to the backend in motoparts-backend.zip.

## Get the APK without installing anything (GitHub Actions)
1. Create a free GitHub account and a new repository (private is fine).
2. Upload ALL files of this folder to the repository (keep the folder structure, including `.github/workflows/build-apk.yml`).
3. IMPORTANT: your backend must be online first (deployed on a server with https). Then in the repo:
   Settings > Secrets and variables > Actions > Variables tab > New repository variable
   Name: `API_BASE_URL`   Value: `https://your-backend-domain.com/api/`
   (If you skip this, the APK points to the emulator address and will not work on a phone.)
4. Go to the Actions tab > "Build APK" > Run workflow (it also runs on every push).
5. When it turns green, open the run and download the artifact `motoparts-debug-apk`. Unzip it to get `app-debug.apk`.
6. Copy it to your Android phone and install (allow "Install unknown apps").

## Included screens
Home (search, brands, categories, products, USD/BDT toggle), brand > model > year > compatible parts,
product page (swipe gallery, tap to zoom, compatibility, estimated-price label, stock, rating),
cart, address management, checkout with real order creation and Stripe payment link, order history,
order detail/tracking, cancel, login/register.

## Not included yet
Wishlist screen, forgot password, writing reviews, promo banner, refund requests, release signing for Play Store,
BDT payment (needs SSLCommerz/bKash merchant account). Cart is kept in memory only (cleared if the app is closed).
Debug APK only. No app icon yet (default Android icon is used).

## Honest status
This code has NOT been compiled or run: my environment has no Android SDK or internet.
The first GitHub build may fail on a small error. Copy the red error text from the Actions log and send it to me; I will fix it.
