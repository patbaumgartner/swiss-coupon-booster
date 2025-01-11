![maven-docker-publish](https://github.com/patbaumgartner/swiss-coupon-booster/actions/workflows/maven-docker-publish.yml/badge.svg)

# SwissCouponBooster

SwissCouponBooster is an application designed to streamline the activation of digital coupons and "Bons" from Migros and Coop in Switzerland, helping users save money effortlessly.

## Features
- Activate Migros and Coop coupons with ease.

## Running the Application

To run the application, the following env variables are needed. Make sure you capture the `datadome` cookie from Supercard.ch first before you run it, it's needed to bypass the geolocation check.

```bash
# Migros Cummulus Account
MIGROS_ACCOUNT_USERNAME=username
MIGROS_ACCOUNT_PASSWORD=password

# Coop Supercard Account
COOP_SUPERCARD_USERNAME=username
COOP_SUPERCARD_PASSWORD=pasword

# Datadome Cookie
COOP_SUPERCARD_DATADOME_COOKIE_VALUE=cookie-value
```

## Contributing

We welcome contributions to SwissCouponBooster! To get started:

1. Fork the repository.
2. Create a new branch for your feature or bug fix:
   ```bash
   git checkout -b your-feature-branch
   ```
3. Commit your changes:
   ```bash
   git commit -m "Add a concise description of your changes"
   ```
4. Push to your branch:
   ```bash
   git push origin your-feature-branch
   ```
5. Open a Pull Request with a detailed description of your changes.

Please ensure your code adheres to our [Code of Conduct](CODE_OF_CONDUCT.md) and includes tests where applicable.

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
