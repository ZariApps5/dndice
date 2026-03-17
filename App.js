import React, { useState, useRef, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  Pressable,
  TextInput,
  ScrollView,
  Animated,
  StatusBar,
  SafeAreaView,
  Platform,
  Dimensions,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';

// ── Config ───────────────────────────────────────────────
const { width: SCREEN_W } = Dimensions.get('window');
// 4 columns, 20px padding each side, 10px gap between columns (3 gaps)
const DIE_SIZE = Math.floor((SCREEN_W - 40 - 30) / 4);

const DICE = [
  { sides: 4,   label: 'd4',   color: '#e74c3c' },
  { sides: 6,   label: 'd6',   color: '#e67e22' },
  { sides: 8,   label: 'd8',   color: '#f1c40f' },
  { sides: 10,  label: 'd10',  color: '#2ecc71' },
  { sides: 12,  label: 'd12',  color: '#3498db' },
  { sides: 20,  label: 'd20',  color: '#9b59b6' },
  { sides: 100, label: 'd%',   color: '#e91e63' },
];

const C = {
  bg:      '#0e0e14',
  surface: '#1a1a26',
  raised:  '#22223a',
  border:  '#2e2e4a',
  accent:  '#c8a84b',
  text:    '#e8e4d9',
  muted:   '#8a8aaa',
  dimBorder: '#3e3e5a',
  red:     '#e74c3c',
  green:   '#2ecc71',
};

const rand = (sides) => Math.floor(Math.random() * sides) + 1;

// ── App ──────────────────────────────────────────────────
export default function App() {
  const initCounts = () => Object.fromEntries(DICE.map(d => [d.sides, 0]));

  const [counts, setCounts]       = useState(initCounts);
  const [modifier, setModifier]   = useState('0');
  const [advantage, setAdvantage] = useState(null); // 'adv' | 'disadv' | null
  const [result, setResult]       = useState(null);
  const [history, setHistory]     = useState([]);
  const [rolling, setRolling]     = useState(false);

  // Animations
  const rollScale    = useRef(new Animated.Value(1)).current;
  const resultOpacity = useRef(new Animated.Value(1)).current;
  const totalScale   = useRef(new Animated.Value(1)).current;

  // ── Interactions ─────────────────────────────────────
  const addDie = useCallback((sides) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    setCounts(p => ({ ...p, [sides]: p[sides] + 1 }));
  }, []);

  const removeDie = useCallback((sides) => {
    setCounts(p => {
      if (p[sides] === 0) return p;
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      return { ...p, [sides]: p[sides] - 1 };
    });
  }, []);

  const clearAll = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setCounts(initCounts());
    setAdvantage(null);
    setModifier('0');
    setResult(null);
  }, []);

  const toggleAdv = useCallback((mode) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    setAdvantage(p => (p === mode ? null : mode));
  }, []);

  // ── Roll ────────────────────────────────────────────
  const doRoll = useCallback(() => {
    if (rolling) return;
    const hasDice = DICE.some(d => counts[d.sides] > 0);
    if (!hasDice) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      return;
    }

    setRolling(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);

    // Button bounce
    Animated.sequence([
      Animated.timing(rollScale, { toValue: 0.94, duration: 80,  useNativeDriver: true }),
      Animated.timing(rollScale, { toValue: 1,    duration: 120, useNativeDriver: true }),
    ]).start();

    // Fade out old result, compute, fade in
    Animated.timing(resultOpacity, { toValue: 0, duration: 120, useNativeDriver: true }).start(() => {
      const mod = parseInt(modifier, 10) || 0;
      const rolls = [];
      let expr = '';

      DICE.forEach(d => {
        if (counts[d.sides] === 0) return;

        if ((advantage === 'adv' || advantage === 'disadv') && d.sides === 20 && counts[d.sides] === 1) {
          const a = rand(20), b = rand(20);
          const kept    = advantage === 'adv' ? Math.max(a, b) : Math.min(a, b);
          rolls.push({ value: kept, sides: d.sides, label: d.label, color: d.color });
        } else {
          for (let i = 0; i < counts[d.sides]; i++) {
            rolls.push({ value: rand(d.sides), sides: d.sides, label: d.label, color: d.color });
          }
        }

        expr += (expr ? ' + ' : '') + (counts[d.sides] > 1 ? counts[d.sides] : '') + d.label;
      });

      const rawSum = rolls.reduce((s, r) => s + r.value, 0);
      const total  = rawSum + mod;
      const isCrit     = rolls.some(r => r.sides === 20 && r.value === 20);
      const isCritFail = !isCrit && rolls.some(r => r.sides === 20 && r.value === 1);

      if (mod !== 0) expr += (mod > 0 ? ' + ' : ' − ') + Math.abs(mod);
      if (advantage === 'adv')    expr += ' (Adv)';
      if (advantage === 'disadv') expr += ' (Dis)';

      setResult({ rolls, total, rawSum, mod, expr, isCrit, isCritFail });

      const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      setHistory(p => [{ expr, total, time }, ...p].slice(0, 5));

      Animated.timing(resultOpacity, { toValue: 1, duration: 180, useNativeDriver: true }).start();

      // Pop the total number
      Animated.sequence([
        Animated.timing(totalScale, { toValue: 1.18, duration: 140, useNativeDriver: true }),
        Animated.timing(totalScale, { toValue: 1,    duration: 200, useNativeDriver: true }),
      ]).start();

      if (isCrit)     Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      if (isCritFail) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);

      setRolling(false);
    });
  }, [rolling, counts, modifier, advantage, rollScale, resultOpacity, totalScale]);

  const hasDice = DICE.some(d => counts[d.sides] > 0);

  // ── Render ───────────────────────────────────────────
  return (
    <SafeAreaView style={s.safe}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />

      <ScrollView
        style={s.scroll}
        contentContainerStyle={s.content}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* ── Header ── */}
        <View style={s.header}>
          <Text style={s.title}>DnDice</Text>
          <Text style={s.subtitle}>ROLL THE BONES</Text>
        </View>

        {/* ── Dice Grid ── */}
        <View style={s.grid}>
          {DICE.map(die => {
            const count  = counts[die.sides];
            const active = count > 0;
            return (
              <Pressable
                key={die.sides}
                onPress={() => addDie(die.sides)}
                onLongPress={() => removeDie(die.sides)}
                style={({ pressed }) => [
                  s.dieBtn,
                  active && { borderColor: die.color, backgroundColor: die.color + '1a' },
                  pressed && s.dieBtnPressed,
                ]}
              >
                {/* Die shape */}
                <DieShape sides={die.sides} color={active ? die.color : C.dimBorder} />

                <Text style={[s.dieLabel, active && { color: die.color }]}>
                  {die.label}
                </Text>

                {active && (
                  <View style={[s.badge, { backgroundColor: die.color }]}>
                    <Text style={s.badgeText}>{count}</Text>
                  </View>
                )}
              </Pressable>
            );
          })}
        </View>

        <Text style={s.hint}>Tap to add  ·  Hold to remove</Text>

        {/* ── Controls row ── */}
        <View style={s.controls}>
          <View style={s.modWrap}>
            <Text style={s.modLabel}>MOD</Text>
            <TextInput
              style={s.modInput}
              value={modifier}
              onChangeText={setModifier}
              keyboardType="numeric"
              selectTextOnFocus
              maxLength={4}
              placeholderTextColor={C.muted}
            />
          </View>

          <Pressable
            onPress={() => toggleAdv('adv')}
            style={[s.advBtn, advantage === 'adv' && s.advActive]}
          >
            <Text style={[s.advText, advantage === 'adv' && { color: C.green }]}>ADV</Text>
          </Pressable>

          <Pressable
            onPress={() => toggleAdv('disadv')}
            style={[s.advBtn, advantage === 'disadv' && s.disadvActive]}
          >
            <Text style={[s.advText, advantage === 'disadv' && { color: C.red }]}>DIS</Text>
          </Pressable>

          <Pressable onPress={clearAll} style={s.clearBtn}>
            <Text style={s.clearText}>Clear</Text>
          </Pressable>
        </View>

        {/* ── Result Panel ── */}
        <Animated.View style={[s.resultPanel, { opacity: resultOpacity }]}>
          {!result ? (
            <Text style={s.resultEmpty}>Select dice and roll</Text>
          ) : (
            <>
              <Text style={s.resultExpr}>{result.expr}</Text>

              {/* Chips */}
              <View style={s.chips}>
                {result.rolls.map((r, i) => (
                  <View
                    key={i}
                    style={[
                      s.chip,
                      r.value === r.sides && { borderColor: r.color },
                      r.value === 1       && s.chipMin,
                    ]}
                  >
                    <Text style={[s.chipVal, r.value === r.sides && { color: r.color }]}>
                      {r.value}
                    </Text>
                    <Text style={s.chipType}>{r.label}</Text>
                  </View>
                ))}
              </View>

              {/* Modifier breakdown */}
              {result.mod !== 0 && (
                <Text style={s.modBreakdown}>
                  {result.rawSum} {result.mod >= 0 ? '+' : '−'} {Math.abs(result.mod)} mod
                </Text>
              )}

              {/* Total */}
              <Animated.Text
                style={[
                  s.total,
                  result.isCrit && s.totalCrit,
                  { transform: [{ scale: totalScale }] },
                ]}
              >
                {result.total}
              </Animated.Text>

              {/* Crit badges */}
              {result.isCrit && (
                <View style={s.critBadge}>
                  <Text style={s.critText}>NATURAL 20  ✦</Text>
                </View>
              )}
              {result.isCritFail && (
                <View style={[s.critBadge, s.failBadge]}>
                  <Text style={[s.critText, { color: C.red }]}>CRITICAL FAIL</Text>
                </View>
              )}
            </>
          )}
        </Animated.View>

        {/* ── History ── */}
        {history.length > 0 && (
          <View style={s.history}>
            <Text style={s.historyTitle}>RECENT ROLLS</Text>
            {history.map((h, i) => (
              <View key={i} style={s.historyRow}>
                <Text style={s.historyExpr} numberOfLines={1}>{h.expr}</Text>
                <Text style={s.historyTotal}>{h.total}</Text>
                <Text style={s.historyTime}>{h.time}</Text>
              </View>
            ))}
          </View>
        )}

        {/* Space for fixed roll button */}
        <View style={{ height: 96 }} />
      </ScrollView>

      {/* ── Fixed Roll Button ── */}
      <View style={s.rollOuter}>
        <Animated.View style={[{ transform: [{ scale: rollScale }] }]}>
          <Pressable
            onPress={doRoll}
            disabled={rolling}
            style={{ borderRadius: 16, overflow: 'hidden' }}
          >
            <LinearGradient
              colors={hasDice
                ? ['#a07820', '#c8a84b', '#d4b860']
                : ['#2a2a3a', '#2a2a3a']}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={s.rollBtn}
            >
              <Text style={[s.rollText, !hasDice && { color: '#555' }]}>
                {rolling ? '· · ·' : 'ROLL'}
              </Text>
            </LinearGradient>
          </Pressable>
        </Animated.View>
      </View>
    </SafeAreaView>
  );
}

// ── Die Shape component ─────────────────────────────────
function DieShape({ sides, color }) {
  const shapes = {
    4:   <Triangle color={color} />,
    6:   <Square   color={color} />,
    8:   <Diamond  color={color} />,
    10:  <Pentagon color={color} />,
    12:  <Decagon  color={color} />,
    20:  <Hexagon  color={color} />,
    100: <Circle   color={color} />,
  };
  return <View style={s.shapeWrap}>{shapes[sides]}</View>;
}

// Geometric shapes using View borders + transforms
const SZ = 28; // base size

function Triangle({ color }) {
  return (
    <View style={{
      width: 0, height: 0,
      borderLeftWidth: SZ * 0.56,
      borderRightWidth: SZ * 0.56,
      borderBottomWidth: SZ,
      borderLeftColor: 'transparent',
      borderRightColor: 'transparent',
      borderBottomColor: color,
    }} />
  );
}

function Square({ color }) {
  return (
    <View style={{
      width: SZ, height: SZ,
      borderRadius: 5,
      borderWidth: 2.5,
      borderColor: color,
    }} />
  );
}

function Diamond({ color }) {
  return (
    <View style={{
      width: SZ, height: SZ,
      borderRadius: 3,
      borderWidth: 2.5,
      borderColor: color,
      transform: [{ rotate: '45deg' }],
    }} />
  );
}

function Pentagon({ color }) {
  // Approximate pentagon with a circle + flat bottom
  return (
    <View style={{
      width: SZ, height: SZ * 0.9,
      borderRadius: SZ * 0.35,
      borderWidth: 2.5,
      borderColor: color,
      borderBottomLeftRadius: 4,
      borderBottomRightRadius: 4,
    }} />
  );
}

function Decagon({ color }) {
  return (
    <View style={{
      width: SZ, height: SZ,
      borderRadius: SZ * 0.3,
      borderWidth: 2.5,
      borderColor: color,
      transform: [{ rotate: '15deg' }],
    }} />
  );
}

function Hexagon({ color }) {
  return (
    <View style={{
      width: SZ, height: SZ * 0.87,
      borderRadius: 4,
      borderWidth: 2.5,
      borderColor: color,
      transform: [{ rotate: '30deg' }],
    }} />
  );
}

function Circle({ color }) {
  return (
    <View style={{
      width: SZ, height: SZ,
      borderRadius: SZ / 2,
      borderWidth: 2.5,
      borderColor: color,
    }} />
  );
}

// ── Styles ───────────────────────────────────────────────
const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.bg },
  scroll: { flex: 1 },
  content: { paddingHorizontal: 20, paddingTop: 28 },

  // Header
  header: { alignItems: 'center', marginBottom: 28 },
  title: {
    fontSize: 38, fontWeight: '900',
    color: C.accent, letterSpacing: 5,
  },
  subtitle: {
    fontSize: 11, color: C.muted,
    letterSpacing: 7, marginTop: 4,
  },

  // Dice grid
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 8,
  },
  dieBtn: {
    width: DIE_SIZE,
    height: DIE_SIZE,
    backgroundColor: C.raised,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: C.border,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    position: 'relative',
  },
  dieBtnPressed: { opacity: 0.65, transform: [{ scale: 0.93 }] },
  shapeWrap: { alignItems: 'center', justifyContent: 'center', height: 32 },
  dieLabel: {
    fontSize: 13, fontWeight: '800',
    color: C.muted, letterSpacing: 1,
  },
  badge: {
    position: 'absolute', top: 5, right: 5,
    minWidth: 18, height: 18, borderRadius: 9,
    alignItems: 'center', justifyContent: 'center',
    paddingHorizontal: 4,
  },
  badgeText: { fontSize: 10, fontWeight: '900', color: '#111' },

  hint: {
    textAlign: 'center',
    color: C.border,
    fontSize: 11,
    letterSpacing: 1,
    marginBottom: 20,
  },

  // Controls
  controls: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    marginBottom: 16,
  },
  modWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: C.raised,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: C.border,
    paddingHorizontal: 10,
    height: 44,
    gap: 6,
  },
  modLabel: { fontSize: 10, fontWeight: '700', color: C.muted, letterSpacing: 2 },
  modInput: {
    color: C.text, fontSize: 16, fontWeight: '700',
    width: 40, textAlign: 'center', padding: 0,
  },
  advBtn: {
    backgroundColor: C.raised,
    borderRadius: 10, borderWidth: 1, borderColor: C.border,
    paddingHorizontal: 12, height: 44,
    alignItems: 'center', justifyContent: 'center',
  },
  advActive:   { borderColor: C.green, backgroundColor: '#1a3a1a' },
  disadvActive: { borderColor: C.red,  backgroundColor: '#3a1a1a' },
  advText: { fontSize: 11, fontWeight: '800', color: C.muted, letterSpacing: 1 },
  clearBtn: {
    marginLeft: 'auto',
    paddingHorizontal: 14, height: 44,
    backgroundColor: C.raised,
    borderRadius: 10, borderWidth: 1, borderColor: C.border,
    alignItems: 'center', justifyContent: 'center',
  },
  clearText: { fontSize: 13, color: C.muted },

  // Result
  resultPanel: {
    backgroundColor: C.surface,
    borderRadius: 16, borderWidth: 1, borderColor: C.border,
    padding: 20, alignItems: 'center',
    minHeight: 110, gap: 10, marginBottom: 24,
  },
  resultEmpty: {
    color: C.muted, fontSize: 13, letterSpacing: 2,
    textTransform: 'uppercase', marginVertical: 20,
  },
  resultExpr: { color: C.muted, fontSize: 12, letterSpacing: 1 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'center' },
  chip: {
    backgroundColor: C.raised, borderRadius: 8,
    borderWidth: 1.5, borderColor: C.border,
    paddingHorizontal: 12, paddingVertical: 6,
    alignItems: 'center', minWidth: 46,
  },
  chipMin: { borderColor: '#444', opacity: 0.5 },
  chipVal: { fontSize: 16, fontWeight: '800', color: C.text },
  chipType: { fontSize: 9, color: C.muted, textTransform: 'uppercase', letterSpacing: 1, marginTop: 1 },
  modBreakdown: { fontSize: 12, color: C.muted },
  total: {
    fontSize: 60, fontWeight: '900', color: C.accent, lineHeight: 68,
  },
  totalCrit: { color: '#f0d080' },
  critBadge: {
    backgroundColor: 'rgba(200,168,75,0.12)',
    borderWidth: 1, borderColor: 'rgba(200,168,75,0.3)',
    borderRadius: 6, paddingHorizontal: 14, paddingVertical: 4,
  },
  failBadge: {
    backgroundColor: 'rgba(231,76,60,0.08)',
    borderColor: 'rgba(231,76,60,0.3)',
  },
  critText: { fontSize: 11, fontWeight: '800', color: C.accent, letterSpacing: 3 },

  // History
  history: { gap: 6, marginBottom: 8 },
  historyTitle: {
    fontSize: 10, fontWeight: '700', color: C.muted,
    letterSpacing: 4, marginBottom: 2, paddingLeft: 2,
  },
  historyRow: {
    backgroundColor: C.surface, borderRadius: 10,
    borderWidth: 1, borderColor: C.border,
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 16, paddingVertical: 12, gap: 10,
  },
  historyExpr: { flex: 1, fontSize: 13, color: C.muted },
  historyTotal: { fontSize: 18, fontWeight: '800', color: C.accent },
  historyTime: { fontSize: 11, color: C.border, minWidth: 42, textAlign: 'right' },

  // Roll button (fixed bottom)
  rollOuter: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    paddingHorizontal: 20,
    paddingBottom: Platform.OS === 'android' ? 20 : 12,
    paddingTop: 10,
    backgroundColor: C.bg + 'f0',
  },
  rollBtn: {
    borderRadius: 16, paddingVertical: 18,
    alignItems: 'center', justifyContent: 'center',
  },
  rollText: { fontSize: 18, fontWeight: '900', color: '#111', letterSpacing: 5 },
});
