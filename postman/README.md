# SafeTransfer Postman Collection

Import `safetransfer.postman_collection.json` into Postman.

Default collection variable:

- `baseUrl`: `http://localhost:8080`

Recommended request order:

1. `Health / Get Health`
2. `Wallets / Create Source Wallet`
3. `Wallets / Create Destination Wallet`
4. `Deposits And Balances / Deposit To Source Wallet`
5. `Transfers / Create Transfer`
6. `Transfers / Get Transfer By Id`
7. `Deposits And Balances / Get Source Balance`
8. `Deposits And Balances / Get Destination Balance`

The create wallet and create transfer requests store response IDs into collection variables:

- `sourceWalletId`
- `destinationWalletId`
- `transferId`
- `idempotencyKey`

Error examples are included for duplicate wallet, invalid currency, deposit validation, insufficient funds, same-wallet transfer, and zero transfer amount.
