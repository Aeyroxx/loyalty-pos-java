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
        // Fixed-ratio layout: the entire content sits inside a Group at design size
        // (1200x680), and a Scale transform shrinks/grows it uniformly with the window.
        // This keeps the layout proportions identical at every window size.
        VBox left = buildBranding();
        StackPane right = buildPinCardWithGlow();
        
        // Use responsive constraints instead of rigid scaling
        left.setMinWidth(320);
        left.setPrefWidth(420);
        left.setMaxWidth(600);
        HBox.setHgrow(left, Priority.ALWAYS);
        
        right.setMinWidth(360);
        right.setPrefWidth(400);
        right.setMaxWidth(460);

        HBox content = new HBox(60, left, right);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(60, 80, 60, 80));
        content.setMaxWidth(1400); // Prevent stretching too wide on ultrawide monitors

        root.getChildren().add(content);
        StackPane.setAlignment(content, Pos.CENTER);

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
                v.setStyle("-fx-stroke: -border;");
                v.setStrokeWidth(1);
                grid.getChildren().add(v);
            }
            for (double y = 0; y <= h; y += 40) {
                Line hl = new Line(0, y, w, y);
                hl.setStyle("-fx-stroke: -border;");
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
        pill.setStyle("-fx-background-color: -overlay-mid; -fx-background-radius: 999; -fx-border-color: -overlay-card; -fx-border-radius: 999;");

        SVGPath shield = makeShieldPath();
        shield.setFill(ACCENT);
        shield.setScaleX(0.55);
        shield.setScaleY(0.55);

        Label text = new Label("POINT OF SALE SYSTEM");
        text.setStyle("-fx-text-fill: -ink-soft; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.15em;");

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
                t.setStyle("-fx-fill: linear-gradient(to bottom, -ink 0%, -ink 50%, -accent 100%);");
            } else {
                t.setStyle("-fx-fill: -ink;");
                t.setOpacity(0.90);
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
        l1.setStyle("-fx-background-color: -overlay-card;");
        HBox.setHgrow(l1, Priority.ALWAYS);

        Label txt = new Label("AUTHORIZED ACCESS ONLY");
        txt.setStyle("-fx-text-fill: -faint; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.2em;");

        Region l2 = new Region();
        l2.setPrefHeight(1);
        l2.setMaxHeight(1);
        l2.setStyle("-fx-background-color: -overlay-card;");
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
        container.setMinWidth(0);
        container.setMaxWidth(Double.MAX_VALUE);
        container.setStyle("-fx-background-color: -overlay-mid; -fx-background-radius: 16; -fx-border-color: -overlay-card; -fx-border-radius: 16;");

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
        strip.setMinWidth(Region.USE_PREF_SIZE); // Prevent clipping/shrinking of children

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
        l.setStyle("-fx-text-fill: -ink-soft; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 12; -fx-font-weight: 700; -fx-letter-spacing: 0.2em;");

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
        l.setStyle("-fx-text-fill: -ink-soft; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.15em;");
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
        l.setStyle("-fx-text-fill: -ink-soft; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-letter-spacing: 0.15em;");
        pill.getChildren().addAll(crown, l);
        return pill;
    }

    private HBox makePillShell() {
        HBox pill = new HBox(6);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setPadding(new Insets(4, 12, 4, 12));
        pill.setStyle("-fx-background-color: -overlay-mid; -fx-background-radius: 999; -fx-border-color: -overlay-card; -fx-border-radius: 999;");
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
        card.setMaxHeight(Region.USE_PREF_SIZE); // Fix tall card issue

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
        r.setStyle("-fx-background-color: -overlay-card;");
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
        if (dev) {
            shieldChip.setStyle("-fx-background-color: rgba(239,68,68,0.15); -fx-background-radius: 14; -fx-border-color: rgba(239,68,68,0.30); -fx-border-radius: 14;");
        } else {
            shieldChip.setStyle("-fx-background-color: -overlay-card; -fx-background-radius: 14; -fx-border-color: -border-strong; -fx-border-radius: 14;");
        }
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
        cardSub.setStyle("-fx-text-fill: -muted; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13;");
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
    /** Count of consecutive failed login attempts for Forgot PIN feature. */
    private int failedAttempts = 0;
    private javafx.scene.control.Button forgotPinBtn;

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
                failedAttempts = 0;
                // Optional second factor: email OTP for admins when otp_login_enabled
                Object enabled = App.ctx.settings.get("otp_login_enabled");
                boolean otpOn = enabled instanceof Boolean ? (Boolean) enabled
                        : "true".equalsIgnoreCase(String.valueOf(enabled));
                if (otpOn && u.isAdmin() && u.email != null && !u.email.isEmpty()) {
                    Platform.runLater(() -> promptOtp(u));
                    return;
                }
                App.ctx.currentUser = u;
                Platform.runLater(App::showShell);
            } else {
                failedAttempts++;
                pad.showError("Incorrect PIN. Try again." + (failedAttempts >= 3 ? " (" + failedAttempts + " attempts)" : ""));
                PauseTransition clearErr = new PauseTransition(Duration.seconds(2));
                clearErr.setOnFinished(e -> pad.clearError());
                clearErr.play();
                // Show Forgot PIN button after 3 consecutive failures
                if (failedAttempts >= 3 && forgotPinBtn == null) {
                    showForgotPinButton();
                }
            }
        } catch (Exception e) {
            pad.showError("Login failed: " + e.getMessage());
        }
    }

    /** Show an OTP entry dialog; verifies via EmailOtpService and proceeds to shell on success. */
    private void promptOtp(User u) {
        com.innov8.loyaltypos.service.EmailOtpService.IssueResult issued =
                com.innov8.loyaltypos.service.EmailOtpService.issue(u.id);

        VBox content = new VBox(14);
        Label header = new Label("Two-step verification");
        header.setStyle("-fx-text-fill: -ink; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 18; -fx-font-weight: 800;");
        Label sub = new Label(issued.delivered
                ? "A 6-digit code was sent to " + maskedEmail(issued.email) + ". It expires in 5 minutes."
                : "Code below — " + (issued.deliveryError == null ? "delivery skipped" : issued.deliveryError));
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: -muted; -fx-font-size: 12;");
        content.getChildren().addAll(header, sub);

        if (!issued.delivered) {
            // Show the code locally when SMTP can't deliver
            Label codeLbl = new Label(issued.code);
            codeLbl.setStyle("-fx-text-fill: -accent; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 32; -fx-font-weight: 700; -fx-padding: 6 12; -fx-background-color: -overlay-mid; -fx-background-radius: 6; -fx-letter-spacing: 0.4em;");
            content.getChildren().add(codeLbl);
        }

        javafx.scene.control.TextField codeTf = new javafx.scene.control.TextField();
        codeTf.setPromptText("000000");
        codeTf.setMaxWidth(220);
        codeTf.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 22; -fx-padding: 8 12; -fx-alignment: CENTER;");
        content.getChildren().add(codeTf);

        Label err = new Label();
        err.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
        err.setVisible(false); err.setManaged(false);
        content.getChildren().add(err);

        javafx.scene.layout.HBox btns = new javafx.scene.layout.HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        javafx.scene.layout.Region sp = new javafx.scene.layout.Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        javafx.scene.control.Button cancel = new javafx.scene.control.Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-ghost");
        javafx.scene.control.Button verify = new javafx.scene.control.Button("Verify");
        verify.getStyleClass().addAll("btn", "btn-primary");
        verify.setDefaultButton(true);
        btns.getChildren().addAll(sp, cancel, verify);
        content.getChildren().add(btns);

        javafx.stage.Window ownerWindow = root.getScene() != null ? root.getScene().getWindow() : App.primaryStage;
        Modal modal = new Modal(ownerWindow, "Verify your email", content);
        cancel.setOnAction(e -> modal.close());
        verify.setOnAction(e -> {
            String code = codeTf.getText() == null ? "" : codeTf.getText().trim();
            if (!com.innov8.loyaltypos.service.EmailOtpService.verify(u.id, code)) {
                err.setText("Incorrect or expired code. Try again or cancel.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            modal.close();
            App.ctx.currentUser = u;
            Platform.runLater(App::showShell);
        });
        modal.show();
    }

    private static String maskedEmail(String email) {
        if (email == null || !email.contains("@")) return email == null ? "" : email;
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "***" + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
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

    // ──────────────────────────────────────────────────────────────────────
    // Forgot PIN feature
    // ──────────────────────────────────────────────────────────────────────
    private void showForgotPinButton() {
        forgotPinBtn = new javafx.scene.control.Button("Forgot PIN?");
        forgotPinBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #d4690a; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 13; -fx-font-weight: 700; -fx-cursor: hand; -fx-underline: true;");
        forgotPinBtn.setOnAction(e -> openForgotPinDialog());
        // Add to the pin card — find the VBox that contains the pad
        if (pad.getParent() instanceof VBox pinCard) {
            pinCard.getChildren().add(forgotPinBtn);
            pinCard.setAlignment(Pos.TOP_CENTER);
        }
    }

    private void openForgotPinDialog() {
        VBox content = new VBox(14);
        Label header = new Label("Forgot your PIN?");
        header.setStyle("-fx-text-fill: -ink; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 18; -fx-font-weight: 800;");
        Label sub = new Label("Enter the email address associated with your account. A verification code will be sent for identity confirmation.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: -muted; -fx-font-size: 12;");

        javafx.scene.control.TextField emailTf = new javafx.scene.control.TextField();
        emailTf.setPromptText("yourname@example.com");
        emailTf.setMaxWidth(360);
        emailTf.setStyle("-fx-font-size: 14; -fx-padding: 8 12;");

        Label err = new Label();
        err.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
        err.setVisible(false); err.setManaged(false);

        content.getChildren().addAll(header, sub, emailTf, err);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        javafx.scene.control.Button cancel = new javafx.scene.control.Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-ghost");
        javafx.scene.control.Button send = new javafx.scene.control.Button("Send Code");
        send.getStyleClass().addAll("btn", "btn-primary");
        send.setDefaultButton(true);
        btns.getChildren().addAll(sp, cancel, send);
        content.getChildren().add(btns);

        javafx.stage.Window ownerWindow = root.getScene() != null ? root.getScene().getWindow() : App.primaryStage;
        Modal modal = new Modal(ownerWindow, "Forgot PIN", content);
        cancel.setOnAction(e -> modal.close());
        send.setOnAction(e -> {
            String email = emailTf.getText() == null ? "" : emailTf.getText().trim();
            if (email.isEmpty() || !email.contains("@")) {
                err.setText("Please enter a valid email address.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            User found = UserService.findByEmail(email);
            if (found == null) {
                err.setText("No active user found with this email address.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            modal.close();
            // Send OTP and open verification dialog
            openForgotPinOtp(found);
        });
        modal.show();
    }

    private void openForgotPinOtp(User u) {
        com.innov8.loyaltypos.service.EmailOtpService.IssueResult issued =
                com.innov8.loyaltypos.service.EmailOtpService.issue(u.id);

        VBox content = new VBox(14);
        Label header = new Label("Enter Verification Code");
        header.setStyle("-fx-text-fill: -ink; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 18; -fx-font-weight: 800;");
        Label sub = new Label(issued.delivered
                ? "A 6-digit code was sent to " + maskedEmail(issued.email) + ". It expires in 5 minutes."
                : "Code below — " + (issued.deliveryError == null ? "delivery skipped" : issued.deliveryError));
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: -muted; -fx-font-size: 12;");
        content.getChildren().addAll(header, sub);

        if (!issued.delivered) {
            Label codeLbl = new Label(issued.code);
            codeLbl.setStyle("-fx-text-fill: -accent; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 32; -fx-font-weight: 700; -fx-padding: 6 12; -fx-background-color: -overlay-mid; -fx-background-radius: 6; -fx-letter-spacing: 0.4em;");
            content.getChildren().add(codeLbl);
        }

        javafx.scene.control.TextField codeTf = new javafx.scene.control.TextField();
        codeTf.setPromptText("000000");
        codeTf.setMaxWidth(220);
        codeTf.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 22; -fx-padding: 8 12; -fx-alignment: CENTER;");
        content.getChildren().add(codeTf);

        Label err = new Label();
        err.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
        err.setVisible(false); err.setManaged(false);
        content.getChildren().add(err);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        javafx.scene.control.Button cancel = new javafx.scene.control.Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-ghost");
        javafx.scene.control.Button verify = new javafx.scene.control.Button("Verify");
        verify.getStyleClass().addAll("btn", "btn-primary");
        verify.setDefaultButton(true);
        btns.getChildren().addAll(sp, cancel, verify);
        content.getChildren().add(btns);

        javafx.stage.Window ownerWindow = root.getScene() != null ? root.getScene().getWindow() : App.primaryStage;
        Modal modal = new Modal(ownerWindow, "Verify Identity", content);
        cancel.setOnAction(e -> modal.close());
        verify.setOnAction(e -> {
            String code = codeTf.getText() == null ? "" : codeTf.getText().trim();
            if (!com.innov8.loyaltypos.service.EmailOtpService.verify(u.id, code)) {
                err.setText("Incorrect or expired code. Try again.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            modal.close();
            openResetPinDialog(u);
        });
        modal.show();
    }

    private void openResetPinDialog(User u) {
        VBox content = new VBox(14);
        Label header = new Label("Create New PIN");
        header.setStyle("-fx-text-fill: -ink; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 18; -fx-font-weight: 800;");
        Label sub = new Label("Enter a new 4–6 digit PIN. You cannot reuse your previous PIN or a PIN already registered to another user.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: -muted; -fx-font-size: 12;");

        javafx.scene.control.TextField pinTf = new javafx.scene.control.TextField();
        pinTf.setPromptText("New PIN (4-6 digits)");
        pinTf.setMaxWidth(220);
        pinTf.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 22; -fx-padding: 8 12; -fx-alignment: CENTER;");
        // Numeric-only filter
        pinTf.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.matches("\\d*")) pinTf.setText(newVal.replaceAll("[^\\d]", ""));
            if (pinTf.getText().length() > 6) pinTf.setText(pinTf.getText().substring(0, 6));
        });

        javafx.scene.control.TextField confirmTf = new javafx.scene.control.TextField();
        confirmTf.setPromptText("Confirm PIN");
        confirmTf.setMaxWidth(220);
        confirmTf.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 22; -fx-padding: 8 12; -fx-alignment: CENTER;");
        confirmTf.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.matches("\\d*")) confirmTf.setText(newVal.replaceAll("[^\\d]", ""));
            if (confirmTf.getText().length() > 6) confirmTf.setText(confirmTf.getText().substring(0, 6));
        });

        Label err = new Label();
        err.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
        err.setVisible(false); err.setManaged(false);

        content.getChildren().addAll(header, sub, pinTf, confirmTf, err);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        javafx.scene.control.Button cancel = new javafx.scene.control.Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-ghost");
        javafx.scene.control.Button reset = new javafx.scene.control.Button("Reset PIN");
        reset.getStyleClass().addAll("btn", "btn-primary");
        reset.setDefaultButton(true);
        btns.getChildren().addAll(sp, cancel, reset);
        content.getChildren().add(btns);

        javafx.stage.Window ownerWindow = root.getScene() != null ? root.getScene().getWindow() : App.primaryStage;
        Modal modal = new Modal(ownerWindow, "Reset PIN", content);
        cancel.setOnAction(e -> modal.close());
        reset.setOnAction(e -> {
            String newPin = pinTf.getText() == null ? "" : pinTf.getText().trim();
            String confirm = confirmTf.getText() == null ? "" : confirmTf.getText().trim();
            err.setVisible(false); err.setManaged(false);

            if (newPin.length() < 4 || newPin.length() > 6) {
                err.setText("PIN must be 4–6 digits.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            if (!newPin.equals(confirm)) {
                err.setText("PINs do not match.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            // Check reuse: cannot match previous PIN
            String oldHash = UserService.getPinHash(u.id);
            if (oldHash != null && oldHash.equals(com.innov8.loyaltypos.util.Hashing.sha256(newPin))) {
                err.setText("You cannot reuse your previous PIN.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            // Check uniqueness: cannot be another user's PIN
            if (UserService.isPinInUse(newPin)) {
                err.setText("This PIN is already registered to another user.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            try {
                UserService.updatePin(u.id, newPin);
                modal.close();
                failedAttempts = 0;
                // Remove the forgot button
                if (forgotPinBtn != null && forgotPinBtn.getParent() instanceof VBox parent) {
                    parent.getChildren().remove(forgotPinBtn);
                    forgotPinBtn = null;
                }
                pad.showError("PIN reset successful! You can now log in.");
                PauseTransition clearMsg = new PauseTransition(Duration.seconds(3));
                clearMsg.setOnFinished(ev -> pad.clearError());
                clearMsg.play();
            } catch (Exception ex) {
                err.setText("Failed to reset PIN: " + ex.getMessage());
                err.setVisible(true); err.setManaged(true);
            }
        });
        modal.show();
    }
}
