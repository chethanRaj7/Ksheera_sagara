# Ksheera-Sagara

Ksheera-Sagara is a native Android dairy profit/loss calculator for farmers who want to understand monthly profit after milk income and input costs.

## What is included

- Daily milk slip income log with liters, fat %, SNF %, slip amount, date, and cow tagging.
- Expense log with Fodder, Medical, Labor, Electricity, and Other categories.
- Room database storage for cows, milk entries, and expense entries.
- Monthly dashboard with income, expenses, net profit, profit per liter, and Green/Red financial health.
- Custom Compose donut chart for expense mix.
- Cow-wise profitability using direct cow expenses plus shared herd cost allocation.
- AI Profit Coach suggestions based on the month’s cost pattern and cow performance.
- Shareable monthly summary as PDF or PNG image.
- Sleek Compose UI with light/dark color schemes, mint finance accents, and smooth tab transitions.

## Build

Open this folder in Android Studio and run the `app` configuration, or use PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

The generated debug APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```
