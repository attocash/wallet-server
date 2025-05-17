Feature: Wallet

  Scenario: Account can be created
    Given the treasury wallet has been imported

    When a new address is created in treasury wallet

    Then account should be created

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

  Scenario: Account should send attos
    Given the treasury wallet has been imported
    And a new address is created in treasury wallet

    When account receives 50 attos
    Then account balance is 50 attos

    When account sends 10 attos
    Then account balance is 40 attos

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
