import { describe, it, expect } from 'vitest';
import { createNewGame } from './gameState';
import {
  CHALLENGES,
  CHALLENGE_BY_ID,
  isTechRestrictedByChallenge,
  disabledTechIdsForChallenge,
  isChallengeRestrictionViolated,
  isChallengeGoalMet,
  computeChallengeRewardEffects,
} from './challenges';

describe('challenge data integrity', () => {
  it('has unique ids', () => {
    const ids = CHALLENGES.map((c) => c.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});

describe('isTechRestrictedByChallenge / disabledTechIdsForChallenge', () => {
  it('Low Carbon disables every coal technology', () => {
    const challenge = CHALLENGE_BY_ID['low_carbon'];
    expect(isTechRestrictedByChallenge(challenge, 'coal_mining')).toBe(true);
    expect(isTechRestrictedByChallenge(challenge, 'coal_power_plant')).toBe(true);
    expect(isTechRestrictedByChallenge(challenge, 'oil_drilling')).toBe(false);
  });

  it('Primitive disables any technology past the Iron Age tier', () => {
    const challenge = CHALLENGE_BY_ID['primitive'];
    expect(isTechRestrictedByChallenge(challenge, 'iron')).toBe(false);
    expect(isTechRestrictedByChallenge(challenge, 'steam_engine')).toBe(true);
  });

  it('disabledTechIdsForChallenge returns an empty set for no active challenge', () => {
    expect(disabledTechIdsForChallenge(null).size).toBe(0);
  });

  it('Single Gas disables any technology producing a gas other than CO2', () => {
    const challenge = CHALLENGE_BY_ID['single_gas'];
    const disabled = disabledTechIdsForChallenge(challenge);
    expect(disabled.has('animal_domestication')).toBe(true); // produces CH4
    expect(disabled.has('controlled_fire')).toBe(false); // produces only CO2
  });
});

describe('isChallengeRestrictionViolated', () => {
  it('flags Ice Age as violated once temperature exceeds the ceiling', () => {
    const challenge = CHALLENGE_BY_ID['ice_age'];
    let state = createNewGame(0);
    expect(isChallengeRestrictionViolated(challenge, state)).toBe(false);
    state = { ...state, temperatureAnomalyC: 3 };
    expect(isChallengeRestrictionViolated(challenge, state)).toBe(true);
  });

  it('challenges with no temperature restriction are never violated by heat', () => {
    const challenge = CHALLENGE_BY_ID['speedrun'];
    const state = { ...createNewGame(0), temperatureAnomalyC: 500 };
    expect(isChallengeRestrictionViolated(challenge, state)).toBe(false);
  });
});

describe('isChallengeGoalMet', () => {
  it('Ice Age is met once civ level reaches the threshold', () => {
    const challenge = CHALLENGE_BY_ID['ice_age'];
    const state = createNewGame(0);
    expect(isChallengeGoalMet(challenge, state, 10)).toBe(false);
    expect(isChallengeGoalMet(challenge, state, 40)).toBe(true);
  });
});

describe('computeChallengeRewardEffects', () => {
  it('is empty with nothing completed', () => {
    expect(computeChallengeRewardEffects({})).toEqual([]);
  });

  it('includes the reward effect for each completed challenge', () => {
    const effects = computeChallengeRewardEffects({ ice_age: true, low_carbon: true });
    expect(effects).toHaveLength(2);
  });
});
