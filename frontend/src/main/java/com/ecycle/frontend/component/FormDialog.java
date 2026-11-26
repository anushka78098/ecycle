package com.ecycle.frontend.component;

import com.ecycle.frontend.api.ApiError;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Generic validated form dialog. The server call runs asynchronously and server-side validation errors are shown inline without closing the dialog. */
public final class FormDialog {
    private FormDialog() {}
    public enum Type { TEXT, AREA, PASSWORD, NUMBER, INT, COMBO, CHECK }
    public record Opt(String value, String label) { @Override public String toString() { return label; } }
    public record F(String key, String label, Type type, boolean required, List<Opt> options, String initial) {}

    public static F text(String k, String l, boolean req) { return new F(k, l, Type.TEXT, req, null, null); }
    public static F text(String k, String l, boolean req, String init) { return new F(k, l, Type.TEXT, req, null, init); }
    public static F area(String k, String l, boolean req) { return new F(k, l, Type.AREA, req, null, null); }
    public static F pass(String k, String l) { return new F(k, l, Type.PASSWORD, true, null, null); }
    public static F num(String k, String l, boolean req, String init) { return new F(k, l, Type.NUMBER, req, null, init); }
    public static F integer(String k, String l, boolean req, String init) { return new F(k, l, Type.INT, req, null, init); }
    public static F check(String k, String l, boolean init) { return new F(k, l, Type.CHECK, false, null, String.valueOf(init)); }
    public static F combo(String k, String l, boolean req, List<Opt> o, String init) { return new F(k, l, Type.COMBO, req, o, init); }
    public static List<Opt> enumOpts(Class<? extends Enum<?>> e, boolean blank) {
        List<Opt> l = new ArrayList<>(); if (blank) l.add(new Opt("", "Any / none"));
        for (Enum<?> c : e.getEnumConstants()) l.add(new Opt(c.name(), J.human(c.name()))); return l;
    }
    public static List<Opt> opts(String... values) { return Arrays.stream(values).map(v -> new Opt(v, J.human(v))).toList(); }

    /** @param call builds the server call from the validated form values; @param done receives its result on the FX thread. */
    public static void show(String title, String okLabel, List<F> fields, Function<ObjectNode, Callable<JsonNode>> call, Consumer<JsonNode> done) {
        show(title, okLabel, fields, null, call, done);
    }

    public static void show(String title, String okLabel, List<F> fields, Node extra, Function<ObjectNode, Callable<JsonNode>> call, Consumer<JsonNode> done) {
        Dialog<Void> d = new Dialog<>(); d.setTitle(title); d.setHeaderText(title);
        d.getDialogPane().getStylesheets().add(Nav.css());
        ButtonType ok = new ButtonType(okLabel, ButtonBar.ButtonData.OK_DONE); d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(10));
        Map<String, Control> ctl = new LinkedHashMap<>(); int r = 0;
        for (F f : fields) {
            Control c = switch (f.type()) {
                case AREA -> { TextArea a = new TextArea(f.initial()); a.setPrefRowCount(3); a.setWrapText(true); yield a; }
                case PASSWORD -> new PasswordField();
                case CHECK -> { CheckBox cb = new CheckBox(); cb.setSelected(Boolean.parseBoolean(f.initial())); yield cb; }
                case COMBO -> { ComboBox<Opt> cb = new ComboBox<>(); cb.getItems().addAll(f.options()); cb.setMaxWidth(Double.MAX_VALUE);
                    f.options().stream().filter(o -> o.value().equals(f.initial())).findFirst().ifPresentOrElse(cb::setValue, () -> { if (!f.options().isEmpty() && f.required()) cb.setValue(f.options().get(0)); }); yield cb; }
                default -> new TextField(f.initial());
            };
            c.setMinWidth(320); ctl.put(f.key(), c);
            g.add(new Label(f.label() + (f.required() ? " *" : "")), 0, r); g.add(c, 1, r++);
        }
        Label err = UI.label("", "error-text"); err.setMaxWidth(460);
        VBox box = new VBox(10, g); if (extra != null) box.getChildren().add(extra); box.getChildren().add(err);
        ScrollPane sp = UI.scroll(box); sp.setPrefViewportHeight(Math.min(560, 90 + fields.size() * 46)); sp.setPrefViewportWidth(560);
        d.getDialogPane().setContent(sp);
        Button okBtn = (Button) d.getDialogPane().lookupButton(ok);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume(); err.setText("");
            ObjectNode body = J.obj();
            for (F f : fields) {
                Control c = ctl.get(f.key()); String v = value(c);
                if (f.required() && (v == null || v.isBlank())) { err.setText(f.label() + " is required"); return; }
                if (v == null || v.isBlank()) { if (f.type() != Type.CHECK) continue; }
                try {
                    switch (f.type()) {
                        case NUMBER -> body.put(f.key(), new BigDecimal(v.trim()));
                        case INT -> body.put(f.key(), Integer.parseInt(v.trim()));
                        case CHECK -> body.put(f.key(), ((CheckBox) c).isSelected());
                        default -> body.put(f.key(), v.trim());
                    }
                } catch (NumberFormatException e) { err.setText(f.label() + " must be a valid number"); return; }
            }
            okBtn.setDisable(true);
            Async.run(call.apply(body), res -> { d.close(); done.accept(res); },
                    t -> { okBtn.setDisable(false); err.setText(t instanceof ApiError a ? a.display() : String.valueOf(t.getMessage())); });
        });
        d.show();
    }

    @SuppressWarnings("unchecked")
    private static String value(Control c) {
        if (c instanceof ComboBox<?> cb) return cb.getValue() == null ? null : ((Opt) cb.getValue()).value();
        if (c instanceof TextInputControl t) return t.getText();
        return null;
    }
}
