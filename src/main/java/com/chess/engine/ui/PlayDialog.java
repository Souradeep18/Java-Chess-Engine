package com.chess.engine.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
  "Play against computer" dialog: time control (unlimited or real time), engine strength 1-8 and side.
  {@link #show(Window)} returns the chosen settings, or null when the dialog was closed.
 */
@SuppressWarnings("serial")
public class PlayDialog extends JDialog {

    private static final String[][] PRESETS = {
            {"1+0", "Bullet"}, {"2+1", "Bullet"}, {"3+0", "Blitz"}, {"3+2", "Blitz"},
            {"5+0", "Blitz"}, {"5+3", "Blitz"}, {"10+0", "Rapid"}, {"10+5", "Rapid"},
            {"15+10", "Rapid"}, {"30+0", "Classical"}, {"30+20", "Classical"}, {"60+0", "Classical"}};

    private PlayConfig result;

    private boolean unlimited = false;
    private int presetIndex = 4;           // 5+0
    private int level = PuzzleProfile.getInt("play.level", 5);
    private int side = 2;                  // random
    private PlayVariant variant = PlayVariant.values()[Math.max(0, Math.min(PlayVariant.values().length - 1, PuzzleProfile.getInt("play.variant", 0)))];
    private final JLabel variantGlyph = UiKit.label(" ", Font.PLAIN, 24, UiKit.TEXT);
    private final JLabel variantName = UiKit.label(" ", Font.BOLD, 17, UiKit.BRIGHT);
    private final JLabel variantDesc = UiKit.label(" ", Font.PLAIN, 12, UiKit.TEXT_DIM);

    private final UiKit.Segmented timeMode;
    private final JPanel realTimePanel = new JPanel();
    private final JPanel unlimitedPanel = new JPanel();
    private final JPanel timeCards = new JPanel(new CardLayout());
    private final UiKit.FlatButton[] presetButtons = new UiKit.FlatButton[PRESETS.length];
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(5, 1, 180, 1));
    private final JSpinner increment = new JSpinner(new SpinnerNumberModel(0, 0, 60, 1));
    private final JLabel timeSummary = UiKit.label(" ", Font.BOLD, 16, UiKit.BRIGHT);
    private final UiKit.Segmented levelBar;
    private final JLabel levelTitle = UiKit.label(" ", Font.BOLD, 15, UiKit.BRIGHT);
    private final JLabel levelText = UiKit.label(" ", Font.PLAIN, 13, UiKit.TEXT);
    private final UiKit.Segmented sideBar;

    private PlayDialog(Window owner) {
        super(owner, "Play against computer", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UiKit.PANEL);
        content.setBorder(new EmptyBorder(14, 26, 10, 26));

        JLabel title = UiKit.label("Play against computer", Font.PLAIN, 26, UiKit.BRIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(10));

        // variant selector: the whole row opens a drop-down with every variant
        JPanel variantRow = new JPanel(new BorderLayout(12, 0));
        variantRow.setBackground(UiKit.PANEL_HI);
        variantRow.setBorder(new EmptyBorder(8, 14, 8, 8));
        JPanel texts = new JPanel(new GridLayout(0, 1));
        texts.setOpaque(false);
        JPanel nameLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        nameLine.setOpaque(false);
        nameLine.add(variantGlyph);
        nameLine.add(variantName);
        texts.add(nameLine);
        JPanel descLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        descLine.setOpaque(false);
        descLine.add(Box.createHorizontalStrut(34));
        descLine.add(variantDesc);
        texts.add(descLine);
        variantRow.add(texts, BorderLayout.CENTER);
        UiKit.FlatButton arrow = new UiKit.FlatButton("\u25BE  Variants", UiKit.PANEL);
        arrow.setFont(UiKit.font(Font.BOLD, 13));
        arrow.setBorder(new EmptyBorder(8, 12, 8, 12));
        arrow.setFocusable(false);
        arrow.setToolTipText("Choose Standard, Chess960, Atomic, Horde, King of the Hill, Anti-chess, Racing Kings or Three-check");
        arrow.addActionListener(e -> showVariantMenu(arrow));
        variantRow.add(arrow, BorderLayout.EAST);
        variantRow.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { showVariantMenu(arrow); }
        });
        variantRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        variantRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        content.add(variantRow);
        content.add(Box.createVerticalStrut(10));
        updateVariant();

        timeMode = new UiKit.Segmented(new String[]{"Unlimited", "Real time"}, 1, i -> {
            unlimited = i == 0;
            ((CardLayout) timeCards.getLayout()).show(timeCards, unlimited ? "u" : "r");
            updateTime();
        });
        timeMode.setAlignmentX(Component.CENTER_ALIGNMENT);
        timeMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        content.add(timeMode);
        content.add(Box.createVerticalStrut(12));

        buildRealTime();
        unlimitedPanel.setOpaque(false);
        unlimitedPanel.add(UiKit.label("Take all the time you need", Font.PLAIN, 17, UiKit.TEXT));
        timeCards.setOpaque(false);
        timeCards.add(unlimitedPanel, "u");
        timeCards.add(realTimePanel, "r");
        ((CardLayout) timeCards.getLayout()).show(timeCards, "r");
        timeCards.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(timeCards);
        content.add(Box.createVerticalStrut(14));

        JLabel strength = UiKit.label("Strength", Font.BOLD, 16, UiKit.TEXT);
        strength.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(strength);
        content.add(Box.createVerticalStrut(6));
        String[] nums = new String[EngineLevel.COUNT];
        for (int i = 0; i < nums.length; i++) nums[i] = String.valueOf(i + 1);
        levelBar = new UiKit.Segmented(nums, level - 1, i -> {
            level = i + 1;
            updateLevel();
        });
        levelBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        levelBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        content.add(levelBar);
        content.add(Box.createVerticalStrut(8));
        levelTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        levelText.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(levelTitle);
        content.add(levelText);
        content.add(Box.createVerticalStrut(14));

        JLabel sideLabel = UiKit.label("Side", Font.BOLD, 16, UiKit.TEXT);
        sideLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(sideLabel);
        content.add(Box.createVerticalStrut(6));
        sideBar = new UiKit.Segmented(new String[]{"♔  White", "♕  Random side", "♚  Black"}, 1, i -> side = i == 1 ? 2 : (i == 0 ? 0 : 1));
        sideBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        sideBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        content.add(sideBar);
        content.add(Box.createVerticalStrut(6));

        // the Play button is pinned below the scrolling settings so it can never be pushed off the screen
        UiKit.FlatButton play = new UiKit.FlatButton("Play against computer", UiKit.GREEN);
        play.setFont(UiKit.font(Font.BOLD, 18));
        play.setForeground(UiKit.BRIGHT);
        play.setPreferredSize(new Dimension(100, 52));
        play.addActionListener(e -> accept());

        JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiKit.PANEL);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(UiKit.PANEL);
        bottom.setBorder(new EmptyBorder(8, 26, 14, 26));
        bottom.add(play, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiKit.PANEL);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(play);
        updateTime();
        updateLevel();
        setMinimumSize(new Dimension(520, 420));
        pack();
        // never taller than the screen (leaves room for the title bar and the task bar)
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.max(getWidth() + 16, 560);
        int height = Math.min(getHeight(), screen.height - 40);
        setSize(width, height);
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { result = null; }
        });
    }

    private void buildRealTime() {
        realTimePanel.setOpaque(false);
        realTimePanel.setLayout(new BoxLayout(realTimePanel, BoxLayout.Y_AXIS));

        JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
        grid.setOpaque(false);
        for (int i = 0; i < PRESETS.length; i++) {
            final int idx = i;
            UiKit.FlatButton b = new UiKit.FlatButton("<html><center><b>" + PRESETS[i][0]
                    + "</b><br><span style='font-size:10px;color:#9a9895'>" + PRESETS[i][1] + "</span></center></html>", UiKit.PANEL_HI);
            b.setBorder(new EmptyBorder(4, 4, 4, 4));
            b.addActionListener(e -> {
                presetIndex = idx;
                String[] parts = PRESETS[idx][0].split("\\+");
                minutes.setValue(Integer.parseInt(parts[0]));
                increment.setValue(Integer.parseInt(parts[1]));
                updateTime();
            });
            presetButtons[i] = b;
            grid.add(b);
        }
        realTimePanel.add(grid);
        realTimePanel.add(Box.createVerticalStrut(10));

        JPanel custom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        custom.setOpaque(false);
        custom.add(UiKit.label("Minutes per side:", Font.PLAIN, 14, UiKit.TEXT));
        custom.add(minutes);
        custom.add(UiKit.label("Increment (s):", Font.PLAIN, 14, UiKit.TEXT));
        custom.add(increment);
        realTimePanel.add(custom);
        realTimePanel.add(Box.createVerticalStrut(8));
        timeSummary.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel sum = new JPanel(new FlowLayout(FlowLayout.CENTER));
        sum.setOpaque(false);
        sum.add(timeSummary);
        realTimePanel.add(sum);

        javax.swing.event.ChangeListener cl = e -> {
            presetIndex = -1;
            String text = minutes.getValue() + "+" + increment.getValue();
            for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i][0].equals(text)) presetIndex = i;
            updateTime();
        };
        minutes.addChangeListener(cl);
        increment.addChangeListener(cl);
        String[] first = PRESETS[presetIndex][0].split("\\+");
        minutes.setValue(Integer.parseInt(first[0]));
        increment.setValue(Integer.parseInt(first[1]));
    }

    private void showVariantMenu(Component owner) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(UiKit.PANEL);
        menu.setBorder(BorderFactory.createLineBorder(UiKit.LINE));
        for (PlayVariant v : PlayVariant.values()) {
            JMenuItem item = new JMenuItem("<html><b>" + v.label + "</b><br><span style='font-size:10px;color:#9a9895'>"
                    + v.description + "</span></html>");
            item.setIcon(new Icon() {
                @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 20));
                    g2.setColor(UiKit.TEXT);
                    g2.drawString(v.glyph, x + 2, y + 20);
                    g2.dispose();
                }
                @Override public int getIconWidth() { return 28; }
                @Override public int getIconHeight() { return 26; }
            });
            item.setBackground(v == variant ? UiKit.PANEL_HI : UiKit.PANEL);
            item.setForeground(UiKit.BRIGHT);
            item.setOpaque(true);
            item.setFont(UiKit.font(Font.PLAIN, 14));
            item.setBorder(new EmptyBorder(6, 10, 6, 14));
            item.addActionListener(e -> {
                variant = v;
                updateVariant();
            });
            menu.add(item);
        }
        menu.show(owner, owner.getWidth() - menu.getPreferredSize().width, owner.getHeight());
    }

    private void updateVariant() {
        variantGlyph.setText(variant.glyph);
        variantGlyph.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 24));
        variantName.setText(variant.label);
        variantDesc.setText(variant.description);
    }

    private void updateTime() {
        for (int i = 0; i < presetButtons.length; i++) presetButtons[i].setSelectedLook(!unlimited && i == presetIndex);
        timeSummary.setText("Time control: " + minutes.getValue() + " min + " + increment.getValue() + " s");
    }

    private void updateLevel() {
        levelTitle.setText(EngineLevel.title(level) + "  (about " + EngineLevel.percent(level) + "% of full strength)");
        levelText.setText("<html><center><body style='width:380px'>" + EngineLevel.blurb(level) + "</body></center></html>");
    }

    private void accept() {
        PuzzleProfile.putInt("play.level", level);
        long base = ((Number) minutes.getValue()).longValue() * 60_000L;
        long inc = ((Number) increment.getValue()).longValue() * 1000L;
        PuzzleProfile.putInt("play.variant", variant.ordinal());
        result = new PlayConfig(level, side, unlimited, base, inc, variant);
        dispose();
    }

    static PlayConfig show(Window owner) {
        PlayDialog d = new PlayDialog(owner);
        d.setVisible(true);
        return d.result;
    }
}