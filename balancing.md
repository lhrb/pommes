# Pommes Connection — Balancing Specification

## 1. Purpose

This document defines the intended economic and competitive behaviour of a seven-day game of **Pommes Connection**.

It provides constraints, targets and evaluation methods without prescribing exact formulas. The game designer may choose any implementation that produces the intended player experience and remains configurable through game data.

---

## 2. Core Game Loop

A game lasts:

* **7 days**
* **20,160 ticks**
* **1 tick every 30 seconds**

The player with the most **Prestige** at the end of the game wins.

The primary player interaction is adjusting dishes, prices and portions to match the current demographic market.

The intended loop is:

1. Observe the market.
2. Adjust the menu.
3. Earn money through sales.
4. Spend money on actions, research and black-market upgrades.
5. Earn Prestige through business performance and strategic decisions.
6. Adapt as the market and competing players change.

Menu quality should remain important throughout the entire week.

---

## 3. Design Pillars

### Menu decisions are the main skill

Players who understand the demographics and adapt their dishes should perform better than players who rely only on upgrades.

Research, actions and black-market upgrades should enhance or redirect menu decisions, not replace them.

### Money enables play; Prestige wins the game

Money is used to develop the business and create opportunities.

A large amount of money should be helpful but should not automatically produce victory.

### Players must specialize

Players should not be able to purchase or maximize every available upgrade during one game.

A successful strategy should require prioritization and opportunity costs.

### The race should remain open

Early success should matter, but it should not make the remainder of the week irrelevant.

Market changes, actions, specialization and bounded luck should allow players to recover through good decisions.

### Results should be understandable

Players should usually be able to explain why they earned or lost money and Prestige.

Randomness may change opportunities, but should not frequently erase several days of progress.

---

## 4. Prestige

Prestige should be earned from several independent sources.

Available sources include:

* Market share per demographic
* Best-selling dishes
* Portions sold
* Money earned
* Regular customers
* Regular-customer retention
* Successful actions
* Strength of an action's target
* Research leadership
* Black-market leadership
* Untaxed income and audits

### Prestige requirements

* Menu and market performance should be the largest combined source.
* No single Prestige source should normally provide more than roughly one third of a winning score.
* A player should be able to compete through two or three strong areas.
* Passive accumulation should be weaker than deliberate strategic play.
* Prestige rewards should favour recent or periodic performance over permanent early leads.

Prestige may be awarded through milestones, periodic rankings, event objectives or final achievements.

The designer may decide the exact method, provided players have multiple viable paths to victory.

### Recommended evaluation windows

Continuous values should preferably be evaluated over periods rather than across the entire game.

Examples:

* Demographic market share: every 6–12 hours
* Best-selling dish: every 12–24 hours
* Regular-customer performance: daily
* Research and black-market leadership: daily
* Portions and earnings: cumulative milestones
* Actions: when their effects expire

This allows leadership to change during the week.

---

## 5. Economy and Player Count

Expected player income depends on:

* Total market demand
* Number of players
* Menu quality
* Competition for demographics
* Research and black-market upgrades
* Regular customers
* Actions and temporary effects

Costs must therefore be balanced against a **reference income**, not against arbitrary fixed values.

For every supported player count and economic phase, determine:

* Median profit per tick
* Lower and upper income ranges
* Median accumulated money
* Median spending by system

The reference income should represent a competent player using reasonable menu choices.

Median values are preferred over averages because unusually successful players should not distort prices.

The designer may either:

* Scale prices by player count, or
* Scale total market demand so that expected individual income remains similar.

Both approaches are valid if the purchasing pace remains consistent.

---

## 6. Economic Phases

The week should be divided into a small number of balancing phases.

Suggested structure:

* **Opening:** first 24 hours
* **Development:** days 2–4
* **Late game:** days 5–7

Each phase may have a different reference income due to accumulated upgrades, regular customers and market development.

Costs may be expressed internally as expected hours of income:

```clojure
cost = reference-profit-per-tick
       × ticks-per-hour
       × target-income-hours
```

The exact implementation may differ, but purchases should have a clear intended saving time.

---

## 7. Actions

Actions are tactical and temporary.

Each player may use at most:

* **2 actions per 12 hours**

Actions also cost money.

The rate limit prevents constant harassment. The money cost creates an opportunity cost.

### Action requirements

A useful action should depend on:

* Timing
* Current demographics
* The acting player's menu
* The selected target
* Competing player strength
* Temporary market conditions

An action should not automatically be profitable.

Expected outcomes:

* Poor use: clearly loses value
* Reasonable use: approximately repays its cost
* Strong use: produces a meaningful advantage
* Exceptional use: provides a memorable result without deciding the entire game

Targeted actions may award more Prestige when used successfully against stronger players.

Prestige should depend on the result of the action, not merely on activating it.

No single action should normally remove more than a modest part of another player's 12-hour progress.

---

## 8. Research

There are five research branches.

Research represents reliable, permanent development for the current weekly game.

### Research requirements

* Research costs increase with each level.
* Later levels require meaningful commitment.
* A typical competitive player should only be able to maximize approximately **two or three research branches**.
* Buying a small amount in every branch should be possible but should sacrifice specialization.
* Research should improve efficiency, flexibility, regular customers or menu performance.
* Pure universal income multipliers should be limited.

Research should be strongest when purchased early enough to repay its cost, but late purchases may still be useful for Prestige or strategic effects.

---

## 9. Black Market

There are three black-market branches.

Black-market upgrades should provide unusual economic advantages, untaxed income or risk management.

### Black-market requirements

* Players should not normally maximize all three branches.
* A typical black-market-focused player should maximize approximately **one or two branches**.
* Benefits may be stronger or less predictable than research.
* Higher investment may increase audit exposure or possible losses.
* Risk should primarily be opt-in and understandable.

Untaxed money should mainly provide economic power. It should not automatically grant large amounts of Prestige.

Audits may produce financial loss, Prestige loss or temporary restrictions, but should not randomly eliminate an entire week's progress.

---

## 10. Regular Customers

Regular customers should reward sustained menu quality and long-term planning.

Relevant measures include:

* Number of regular customers
* Conversion rate
* Retention rate
* Revenue generated by regulars

Regular customers may provide both income and Prestige, but they should not become a completely separate passive economy.

Menu quality and demographic suitability should continue to influence their behaviour.

---

## 11. Spending Capacity

The total economy should not allow players to purchase everything.

A competitive player should normally be forced to choose between:

* Research
* Black-market upgrades
* Actions
* Holding money for future opportunities

Adding a new purchasable system should require one of the following:

* Reallocating the existing spending budget
* Adding a new non-money constraint
* Increasing income together with all affected prices

New systems should not simply add additional mandatory spending.

Useful non-money constraints include:

* Charges
* Cooldowns
* Upgrade slots
* Mutually exclusive branches
* Risk
* Time
* Limited availability

---

## 12. Luck and Catch-Up

Luck should change the strategic situation more often than it directly changes the score.

Suitable randomness includes:

* Demographic shifts
* Temporary city events
* Audit checks
* Variable action effectiveness
* Limited black-market opportunities

Catch-up mechanics should provide opportunities rather than free Prestige.

Suitable mechanisms include:

* Rewards for successful actions against stronger players
* Profitable neglected demographics
* Market saturation around dominant strategies
* Temporary objectives for trailing players
* Stronger late-game opportunities
* Bounded action or upgrade discounts

A player who is behind halfway through the game should still have a plausible path to victory through good play.

---

## 13. Simulation

The live game rules and the balancing simulation should use the same core functions.

Simulated agents do not need to imitate humans perfectly. They should represent distinct strategies.

Minimum agent types:

* Adaptive menu player
* Research specialist
* Black-market specialist
* Action-focused player
* Regular-customer specialist
* Money hoarder
* Random legal player

Each strategy should be tested with different activity levels, such as:

* Two decisions per day
* Four decisions per day
* Eight decisions per day

Simulations should cover all supported player counts and multiple random seeds.

---

## 14. Required Metrics

The simulation should record:

* Profit by game phase
* Final Prestige
* Prestige by source
* Market share by demographic
* Best-selling dishes
* Portions sold
* Actions used
* Action value and target
* Research levels
* Black-market levels
* Regular customers and retention
* Untaxed income
* Audits
* Unspent money
* Leader changes
* Rank during the game
* Final rank

Aggregate results should show:

* Win rate by strategy
* Income by player count
* Prestige distribution by source
* Typical number of maximized branches
* Action usage and effectiveness
* Activity-frequency advantage
* Comeback frequency

---

## 15. Initial Acceptance Targets

These are broad indicators, not strict mathematical requirements.

* No single strategy consistently wins more than roughly 40% of mixed simulations.
* Menu and market performance provide approximately 25–40% of winning Prestige.
* No single Prestige source normally exceeds approximately 35%.
* Competitive players maximize around two or three research branches.
* Black-market-focused players maximize around one or two black-market branches.
* Most players use a meaningful portion of their available actions.
* Good action timing is noticeably stronger than random action usage.
* The leader changes multiple times in a typical game.
* Increased activity provides an advantage, but does not overwhelm strategy.
* A player outside first place halfway through the week sometimes wins.
* Players finish with too little money to purchase every desirable upgrade.

Failing a target should trigger inspection, not necessarily an automatic balance change.

---

## 16. Designer Freedom

The game designer may freely choose:

* Exact Prestige values
* Award intervals
* Cost curves
* Upgrade effects
* Action durations
* Audit probabilities
* Phase boundaries
* Catch-up implementations
* Market event frequency

Changes are valid when they preserve the core principles:

1. Menu adaptation remains the central skill.
2. Money creates meaningful choices.
3. Players cannot purchase everything.
4. Several Prestige strategies remain viable.
5. The weekly race stays competitive.
6. Rules remain understandable and configurable.
