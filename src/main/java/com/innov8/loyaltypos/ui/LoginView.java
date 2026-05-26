package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.User;
import com.innov8.loyaltypos.service.UserService;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

/**
 * Login screen — pixel-for-pixel match with the React Electron version
 * (loyalty-pos/src/pages/Login.jsx). Implements ambient glows, grid
 * texture, fade-slide-in animations, marquee feature strip, gradient
 * business name title, animated ping pill, dev-mode badge, and card glow.
 */
public class LoginView {
    private static final String DEV_PIN = "082827";
    private static final Color ACCENT = Color.web("#d4690a");
    private static final Color DANGER = Color.web("#ef4444");

    private final StackPane root = new StackPane();
    private PinPad pad;

    // dev-mode reactive UI bits, redrawn when toggled
    private SVGPath shieldIcon;
    private Region shieldChip;
    private Label cardTitle;
    private Label cardSub;
    private Label devBadge;
    private HBox titleRowRef;

    /** Long-lived animations stopped when the view detaches, to avoid leaks on dev-mode toggle. */
    private final java.util.List<Animation> animations = new java.util.ArrayList<>();

    public LoginView() {
        root.getStyleClass().add("login-root");
        // Allow the view to shrink with the window — no fixed pref size.
        root.setMinSize(0, 0);
        root.setStyle("-fx-background-color: -paper;");

        // Layer 1: ambient amber glow top-right + soft white glow bottom-left
        root.getChildren().add(buildAmbientGlows());

        // Layer 2: subtle grid texture (40px squares, 2.5% opacity)
        root.getChildren().add(buildGridTexture());

        // Layer 3: branding LEFT, PIN RIGHT (matches the original React design).
        // HBox + ScrollPane: side-by-side stays stable, vertical scroll on small windows.
        javafx.scene.control.ScrollPane scroller = new javafx.scene.control.ScrollPane();
        scroller.setFitToWidth(true);
        scroller.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroller.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox left = buildBranding();
        StackPane right = buildPinCardWithGlow();
        left.setMinWidth(420);
        left.setPrefWidth(500);
        left.setMaxWidth(560);
        right.setMinWidth(420);

        HBox content = new HBox(60, left, right);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40, 40, 40, 40));
        HBox.setHgrow(left, Priority.SOMETIMES);
        HBox.setHgrow(right, Priority.SOMETIMES);

        scroller.setContent(content);

        root.getChildren().add(scroller);
        StackPane.setAlignment(scroller, Pos.CENTER);

        // staggered fade-slide-in (matches React keyframes)
        animateFadeIn(left, Duration.millis(100));
        animateFadeIn(right, Duration.millis(600));

        // Stop long-lived animations when the view is detached (e.g. on dev-mode toggle
        // which rebuilds LoginView). Without this, TranslateTransition + ping ScaleTransition
        // keep firing on orphaned nodes and the JavaFX master timer leaks references.
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) stopAllAnimations();
        });
    }

    private void stopAllAnimations() {
        for (Animation a : animations) {
            try { a.stop(); } catch (Exception ignore) {}
        }
        animations.clear();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Ambient glow layer
    // ──────────────────────────────────────────────────────────────────────
    private Pane buildAmbientGlows() {
        Pane p = new Pane();
        p.setMouseTransparent(true);
        p.setPickOnBounds(false);

        // Top-right amber glow (600x600, 10% opacity, 120px blur)
        Circle amber = new Circle(300, 300, 300);
        amber.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#d4690a", 0.10)),
                new Stop(1, Color.web("#d4690a", 0.0))));
        amber.setEffect(new GaussianBlur(120));
        amber.setCache(true);
        amber.setCacheHint(CacheHint.SPEED);
        // anchored top-right (no margin) — Pane uses absolute positioning
        amber.layoutXProperty().bind(p.widthProperty().subtract(0));
        amber.layoutYProperty().set(0);
        amber.setTranslateX(-300); // anchor right edge to right side
        p.getChildren().add(amber);

        // Bottom-left soft white glow (400x400, 3% opacity, 100px blur)
        Circle white = new Circle(200, 200, 200);
        white.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffffff", 0.03)),
                new Stop(1, Color.web("#ffffff", 0.0))));
        white.setEffect(new GaussianBlur(100));
        white.setCache(true);
        white.setCacheHint(CacheHint.SPEED);
        white.layoutXProperty().set(0);
        white.layoutYProperty().bind(p.heightProperty().subtract(400));
        p.getChildren().add(white);

        return p;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Grid texture (repeating 40px lines @ 2.5% opacity)
    // ──────────────────────────────────────────────────────────────────────
    private Pane buildGridTexture() {
        Pane grid = new Pane();
        grid.setMouseTransparent(true);
        grid.setPickOnBounds(false);
        grid.setOpacity(0.025);

        // Lines are rebuilt when size changes (cheap; lines are simple Shape nodes)
        Runnable rebuild = () -> {
            grid.getChildren().clear();
            double w = grid.getWidth();
            double h = grid.getHeight();
            if (w <= 0 || h <= 0) return;
            for (double x = 0; x <= w; x += 40) {
                Line v = new Line(x, 0, x, h);
                v.setStroke(Color.WHITE);
                v.setStrokeWidth(1);
                grid.getChildren().add(v);
            }
            for (double y = 0; y <= h; y += 40) {
                Line hl = new Line(0, y, w, y);
                hl.setStroke(Color.WHITE);
                hl.setStrokeWidth(1);
                grid.getChildren().add(hl);
            }
        };
        grid.widthProperty().addListener((o, a, b) -> rebuild.run());
        grid.heightProperty().addListener((o, a, b) -> rebuild.run());
        return grid;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Left column: branding
    // ──────────────────────────────────────────────────────────────────────
    private VBox buildBranding() {
        VBox box = new VBox(22);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(560);

        box.getChildren().add(buildLogoMark());
        box.getChildren().add(buildBadge());
        box.getChildren().add(buildTitle());
        box.getChildren().add(buildDivider());
        box.getChildren().add(buildMarquee());
        box.getChildren().add(buildStatusPills());
        return box;
    }

    /** Logo mark — tries to load /com/innov8/loyaltypos/img/logo.png; falls back to an amber square. */
    private javafx.scene.Node buildLogoMark() {
        try {
            var url = LoginView.class.getResource("/com/innov8/loyaltypos/img/logo.png");
            if (url != null) {
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(new javafx.scene.image.Image(url.toExternalForm()));
                iv.setFitHeight(72);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                return iv;
            }
        } catch (Exception ignore) {}
        // Fallback: stylized monogram "LP" inside an amber rounded square
        StackPane mark = new StackPane();
        mark.setPrefSize(72, 72);
        mark.setMinSize(72, 72);
        mark.setMaxSize(72, 72);
        mark.setBackground(new Background(new BackgroundFill(ACCENT, new CornerRadii(14), Insets.EMPTY)));
        mark.setEffect(new javafx.scene.effect.DropShadow(18, Color.web("#d4690a", 0.55)));
        Label lp = new Label("LP");
        lp.setStyle("-fx-text-fill: white; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 32; -fx-font-weight: 800; -fx-letter-spacing: 0.04em;");
        mark.getChildren().add(lp);
        return mark;
    }

    private HBox buildBadge() {
        HBox pill = new HBox(8);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setMaxWidth(Region.USE_PREF_SIZE);
        pill.setPadding(new Insets(6, 14, 6, 14));
        pill.setBackground(new Background(new BackgroundFill(
                Color.web("#ffffff", 0.05), new CornerRadii(999), Insets.EMPTY)));
        pill.setBorder(new Border(new BorderStroke(
                Color.web("#ffffff", 0.10), BorderStrokeStyle.SOLID,
                new CornerRadii(999), new BorderWidths(1))));

        SVGPath shield = makeShieldPath();
        shield.setFill(ACCENT);
        shield.setScaleX(0.55);
        shield.setScaleY(0.55);

        Label text = new Label("POINT OF SALE SYSTEM");
        text.setStyle("-fx-text-fill: #d1d5db; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.15em;");

        pill.getChildren().addAll(shield, text);
        HBox wrap = new HBox(pill);
        return wrap;
    }

    private TextFlow buildTitle() {
        String name = (String) App.ctx.settings.getOrDefault("business_name", "Loyalty POS");
        if (name == null || name.isEmpty()) name = "Loyalty POS";
        String[] words = name.split(" ");

        TextFlow flow = new TextFlow();
        flow.setMaxWidth(600);
        for (int i = 0; i < words.length; i++) {
            Text t = new Text(words[i] + (i < words.length - 1 ? " " : ""));
            t.setFont(Font.font("Barlow Condensed", FontWeight.EXTRA_BOLD, 56));
            if (i == 0) {
                // gradient white→amber (matches React from-white via-white to-[#d4690a])
                t.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#ffffff")),
                        new Stop(0.5, Color.web("#ffffff")),
                        new Stop(1, ACCENT)));
            } else {
                // Color.web() requires a literal hex — looked-up colors only work in CSS.
                t.setFill(Color.web("#f4f4f5", 0.90));
            }
            flow.getChildren().add(t);
        }
        return flow;
    }

    private HBox buildDivider() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);

        Region l1 = new Region();
        l1.setPrefHeight(1);
        l1.setMaxHeight(1);
        l1.setBackground(new Background(new BackgroundFill(Color.web("#ffffff", 0.10), CornerRadii.EMPTY, Insets.EMPTY)));
        HBox.setHgrow(l1, Priority.ALWAYS);

        Label txt = new Label("AUTHORIZED ACCESS ONLY");
        txt.setStyle("-fx-text-fill: -faint; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.2em;");

        Region l2 = new Region();
        l2.setPrefHeight(1);
        l2.setMaxHeight(1);
        l2.setBackground(new Background(new BackgroundFill(Color.web("#ffffff", 0.10), CornerRadii.EMPTY, Insets.EMPTY)));
        HBox.setHgrow(l2, Priority.ALWAYS);

        row.getChildren().addAll(l1, txt, l2);
        return row;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Feature marquee — scrolls left infinitely
    // ──────────────────────────────────────────────────────────────────────
    private static final String[] FEATURES = {
            "POS CHECKOUT", "TRUCK TRACKING", "INVENTORY", "REPORTS",
            "PO ACCOUNTS", "TRUCK TRACKING", "INVENTORY", "REPORTS"
    };

    private StackPane buildMarquee() {
        StackPane container = new StackPane();
        container.setPrefHeight(56);
        container.setMaxHeight(56);
        container.setBackground(new Background(new BackgroundFill(
                Color.web("#ffffff", 0.05), new CornerRadii(16), Insets.EMPTY)));
        container.setBorder(new Border(new BorderStroke(
                Color.web("#ffffff", 0.10), BorderStrokeStyle.SOLID,
                new CornerRadii(16), new BorderWidths(1))));

        // Clip with rounded corners
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(container.widthProperty());
        clip.heightProperty().bind(container.heightProperty());
        container.setClip(clip);

        HBox strip = new HBox(32);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.setPadding(new Insets(0, 16, 0, 16));

        // Build features twice so it loops seamlessly
        for (int round = 0; round < 2; round++) {
            for (String label : FEATURES) {
                strip.getChildren().add(makeFeatureItem(label));
            }
        }
        container.getChildren().add(strip);
        StackPane.setAlignment(strip, Pos.CENTER_LEFT);

        // Animate translateX from 0 → -(half width) repeating
        strip.applyCss();
        strip.layout();
        Platform.runLater(() -> {
            double full = strip.getWidth();
            double half = full / 2.0;
            if (half <= 0) return;
            TranslateTransition tt = new TranslateTransition(Duration.seconds(30), strip);
            tt.setFromX(0);
            tt.setToX(-half);
            tt.setInterpolator(Interpolator.LINEAR);
            tt.setCycleCount(TranslateTransition.INDEFINITE);
            animations.add(tt);
            tt.play();
        });

        return container;
    }

    private HBox makeFeatureItem(String label) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setOpacity(0.5);

        SVGPath dot = new SVGPath();
        dot.setContent("M 8 0 L 16 16 L 0 16 Z"); // small triangular mark
        dot.setFill(ACCENT);
        dot.setScaleX(0.7);
        dot.setScaleY(0.7);

        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #d1d5db; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 12; -fx-font-weight: 700; -fx-letter-spacing: 0.2em;");

        item.getChildren().addAll(dot, l);
        return item;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Status pills with animated ping
    // ──────────────────────────────────────────────────────────────────────
    private HBox buildStatusPills() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(
                pingPill("SYSTEM ONLINE", Color.web("#22c55e"), true),
                iconPill("OFFLINE READY", ACCENT));
        return row;
    }

    private HBox pingPill(String text, Color dotColor, boolean animate) {
        HBox pill = makePillShell();
        StackPane dotWrap = new StackPane();
        dotWrap.setPrefSize(10, 10);

        Circle ping = new Circle(5, dotColor.deriveColor(0, 1, 1, 0.75));
        Circle dot = new Circle(4, dotColor);
        dotWrap.getChildren().addAll(ping, dot);

        if (animate) {
            ScaleTransition st = new ScaleTransition(Duration.seconds(1.0), ping);
            st.setFromX(0.6); st.setFromY(0.6);
            st.setToX(2.2);   st.setToY(2.2);
            st.setCycleCount(ScaleTransition.INDEFINITE);
            FadeTransition ft = new FadeTransition(Duration.seconds(1.0), ping);
            ft.setFromValue(0.75);
            ft.setToValue(0.0);
            ft.setCycleCount(FadeTransition.INDEFINITE);
            animations.add(st);
            animations.add(ft);
            st.play(); ft.play();
        }

        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #d1d5db; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.15em;");
        pill.getChildren().addAll(dotWrap, l);
        return pill;
    }

    private HBox iconPill(String text, Color iconColor) {
        HBox pill = makePillShell();
        SVGPath crown = new SVGPath();
        crown.setContent("M2 14h12l-1-6-3 2-2-4-2 4-3-2-1 6z");
        crown.setFill(iconColor);
        crown.setScaleX(0.65);
        crown.setScaleY(0.65);

        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #d1d5db; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.15em;");
        pill.getChildren().addAll(crown, l);
        return pill;
    }

    private HBox makePillShell() {
        HBox pill = new HBox(6);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setPadding(new Insets(4, 12, 4, 12));
        pill.setBackground(new Background(new BackgroundFill(
                Color.web("#ffffff", 0.05), new CornerRadii(999), Insets.EMPTY)));
        pill.setBorder(new Border(new BorderStroke(
                Color.web("#ffffff", 0.10), BorderStrokeStyle.SOLID,
                new CornerRadii(999), new BorderWidths(1))));
        return pill;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Right column: PIN card wrapped in glow halo
    // ──────────────────────────────────────────────────────────────────────
    private StackPane buildPinCardWithGlow() {
        StackPane wrap = new StackPane();
        wrap.setMaxWidth(Region.USE_PREF_SIZE);

        // Soft amber halo behind the card (top-right blob, 15% opacity, 80px blur)
        Circle halo = new Circle(112);
        halo.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#d4690a", 0.15)),
                new Stop(1, Color.web("#d4690a", 0.0))));
        halo.setEffect(new GaussianBlur(80));
        halo.setMouseTransparent(true);
        halo.setTranslateX(140);
        halo.setTranslateY(-150);

        VBox card = buildPinCard();
        wrap.getChildren().addAll(halo, card);
        StackPane.setAlignment(halo, Pos.TOP_RIGHT);
        return wrap;
    }

    private VBox buildPinCard() {
        VBox card = new VBox(24);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(40));
        card.setAlignment(Pos.TOP_CENTER);
        card.setMinWidth(420);
        card.setMaxWidth(440);

        // Header: shield-icon chip + title/sub stack (left-aligned, matches React)
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        shieldChip = new StackPane();
        shieldChip.setMinSize(44, 44);
        shieldChip.setPrefSize(44, 44);
        shieldChip.setMaxSize(44, 44);
        applyShieldChipStyle();
        shieldIcon = makeShieldPath();
        applyShieldIconStyle();
        ((StackPane) shieldChip).getChildren().add(shieldIcon);

        VBox titleBox = new VBox(2);
        HBox titleRow = new HBox(8);
        titleRowRef = titleRow;
        titleRow.setAlignment(Pos.CENTER_LEFT);

        cardTitle = new Label();
        applyTitleStyle();

        devBadge = new Label("DEV");
        devBadge.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 9; -fx-font-weight: 700; -fx-text-fill: #ef4444; -fx-background-color: rgba(239,68,68,0.15); -fx-border-color: rgba(239,68,68,0.4); -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 1 5; -fx-letter-spacing: 0.08em;");

        titleRow.getChildren().add(cardTitle);
        cardSub = new Label();
        applySubStyle();

        titleBox.getChildren().addAll(titleRow, cardSub);
        header.getChildren().addAll(shieldChip, titleBox);
        refreshDevState(); // sets text + badge based on current devMode

        card.getChildren().add(header);
        card.getChildren().add(makeHairline());

        pad = new PinPad(this::handlePin);
        card.getChildren().add(pad);

        card.getChildren().add(makeHairline());

        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_LEFT);
        Label v = new Label("v1.3.5");
        v.setStyle("-fx-text-fill: -faint; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 10; -fx-letter-spacing: 0.15em;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label brand = new Label("LOYALTY POS");
        brand.setStyle("-fx-text-fill: -faint; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 10; -fx-letter-spacing: 0.15em;");
        footer.getChildren().addAll(v, spacer, brand);
        card.getChildren().add(footer);

        return card;
    }

    private Region makeHairline() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMaxHeight(1);
        r.setBackground(new Background(new BackgroundFill(Color.web("#ffffff", 0.10), CornerRadii.EMPTY, Insets.EMPTY)));
        return r;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Dev-mode visual state
    // ──────────────────────────────────────────────────────────────────────
    private void refreshDevState() {
        boolean dev = Database.isDevMode();
        cardTitle.setText(dev ? "DEV MODE — STAFF LOGIN" : "STAFF LOGIN");
        cardSub.setText(dev ? "Dev DB active — default PIN: 1234" : "Enter your PIN (4–6 digits)");

        if (titleRowRef != null) {
            titleRowRef.getChildren().remove(devBadge);
            if (dev) titleRowRef.getChildren().add(devBadge);
        }

        applyShieldChipStyle();
        applyShieldIconStyle();
    }

    private void applyShieldChipStyle() {
        boolean dev = Database.isDevMode();
        Color bg = dev ? Color.web("#ef4444", 0.15) : Color.web("#ffffff", 0.10);
        Color ring = dev ? Color.web("#ef4444", 0.30) : Color.web("#ffffff", 0.20);
        shieldChip.setBackground(new Background(new BackgroundFill(bg, new CornerRadii(14), Insets.EMPTY)));
        shieldChip.setBorder(new Border(new BorderStroke(ring, BorderStrokeStyle.SOLID, new CornerRadii(14), new BorderWidths(1))));
    }

    private void applyShieldIconStyle() {
        boolean dev = Database.isDevMode();
        shieldIcon.setFill(dev ? DANGER : ACCENT);
        shieldIcon.setScaleX(0.85);
        shieldIcon.setScaleY(0.85);
    }

    private void applyTitleStyle() {
        cardTitle.setStyle("-fx-text-fill: -ink-soft; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700; -fx-letter-spacing: 0.2em;");
    }

    private void applySubStyle() {
        cardSub.setStyle("-fx-text-fill: #ffffff80; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13;");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Animations
    // ──────────────────────────────────────────────────────────────────────
    private void animateFadeIn(Region node, Duration delay) {
        node.setOpacity(0);
        node.setTranslateY(20);
        PauseTransition pause = new PauseTransition(delay);
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(node.opacityProperty(), 0),
                        new KeyValue(node.translateYProperty(), 20)),
                new KeyFrame(Duration.millis(700),
                        new KeyValue(node.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(node.translateYProperty(), 0, Interpolator.EASE_OUT)));
        new SequentialTransition(pause, tl).play();
    }

    // ──────────────────────────────────────────────────────────────────────
    // PIN handling
    // ──────────────────────────────────────────────────────────────────────
    private void handlePin(String pin) {
        if (DEV_PIN.equals(pin)) {
            Database.setDevMode(!Database.isDevMode());
            App.ctx.devMode = Database.isDevMode();
            App.ctx.settings = com.innov8.loyaltypos.service.SettingsService.getAll();
            App.showLogin();
            return;
        }
        try {
            User u = UserService.login(pin);
            if (u != null) {
                App.ctx.currentUser = u;
                Platform.runLater(App::showShell);
            } else {
                pad.showError("Incorrect PIN. Try again.");
                PauseTransition clearErr = new PauseTransition(Duration.seconds(2));
                clearErr.setOnFinished(e -> pad.clearError());
                clearErr.play();
            }
        } catch (Exception e) {
            pad.showError("Login failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────
    private static SVGPath makeShieldPath() {
        // lucide-react "shield-check" silhouette (24x24 viewbox)
        SVGPath p = new SVGPath();
        p.setContent("M12 2L4 5v6c0 5 3.5 9.5 8 11 4.5-1.5 8-6 8-11V5l-8-3zm-1 14l-4-4 1.5-1.5L11 13l4.5-4.5L17 10l-6 6z");
        return p;
    }

    public Region getRoot() { return root; }
}
