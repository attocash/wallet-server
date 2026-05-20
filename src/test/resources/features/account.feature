Feature: Wallet

  Scenario: Account can be created
    Given the treasury wallet has been imported

    When a new address is created in treasury wallet

    Then account should be created

  Scenario: Wallet accounts can be created through an account index
    Given the treasury wallet has been imported

    When the treasury wallet creates accounts through account index 2

    Then 3 accounts should exist in treasury wallet

  Scenario: Account can be disabled
    Given the treasury wallet has been imported

    When a new address is created in treasury wallet
    And address is disabled

    Then account should be disabled

  Scenario: Account can be enabled
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When address is disabled
    Then account should be disabled

    When address is enabled
    Then account should be enabled

  Scenario: Account can be enabled after being disabled
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When address is disabled
    Then account should be disabled

    When address is enabled
    Then account should be enabled

  Scenario: Account can be enabled after being disabled
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When address is disabled
    Then account should be disabled

    When address is enabled
    Then account should be enabled

  Scenario: Account should open account when receives attos
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos

    Then account balance is 50 attos

  Scenario: Wallet accounts can be recovered from an imported wallet
    Given the treasury wallet has been imported

    When the treasury wallet is recovered with gap limit 3
    Then recovery should start at account index 0
    And recovery should stop at account index 2
    And recovery should scan 3 account indexes
    Then 3 accounts should exist in treasury wallet

    When recovered account index 1 receives 50 attos in treasury wallet
    Then account balance is 50 attos

  Scenario: Wallet recovery starts from the latest known account
    Given the treasury wallet has been imported
    And the treasury wallet creates accounts through account index 2

    When the treasury wallet is recovered with gap limit 3
    Then recovery should start at account index 2
    And recovery should stop at account index 4
    And recovery should scan 3 account indexes
    Then 5 accounts should exist in treasury wallet

  Scenario: Wallet recovery creates accounts through the latest opened account
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos
    Then account balance is 50 attos

    When the treasury wallet is recovered with gap limit 3
    Then recovery should start at account index 0
    And recovery should stop at account index 0
    And recovery should scan 4 account indexes
    Then 1 accounts should exist in treasury wallet

  Scenario: Account should send attos
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos
    Then account balance is 50 attos

    When account sends 10 attos
    Then account balance is 40 attos

  Scenario: Account should reject concurrent sends with the same last height
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos
    Then account balance is 50 attos

    When two concurrent sends of 10 attos use the same last height
    Then one send should succeed and one should conflict
    Then account balance is 40 attos

  Scenario: Unopened account send should still report unopened when wallet is locked
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When the treasury wallet is locked
    And account tries to send 1 attos

    Then request should fail because account is not open

  Scenario: Open account send should report locked wallet
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos
    Then account balance is 50 attos

    When the treasury wallet is locked
    And account tries to send 1 attos

    Then request should fail because the treasury wallet is locked

  Scenario: Account should change representative
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos
    Then account balance is 50 attos

    When account representative changes to awesome
    Then account representative is awesome

  Scenario: Account entries should be streamable
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos
    Then account balance is 50 attos

    When entry is added

    Then entries are streamable
