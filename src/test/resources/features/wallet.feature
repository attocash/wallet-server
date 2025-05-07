Feature: Wallet

  Scenario: Locking a wallet
    Given the treasury wallet has been imported

    When the treasury wallet is locked

    Then the treasury wallet status should be LOCKED


  Scenario: Unlocking a wallet
    Given the treasury wallet has been imported

    When the treasury wallet is locked
    When the treasury wallet is unlocked

    Then the treasury wallet status should be UNLOCKED
