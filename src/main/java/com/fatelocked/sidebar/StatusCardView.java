package com.fatelocked.sidebar;

import com.fatelocked.ui.Card;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Flow;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.TextBlock;
import com.fatelocked.ui.Type;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The status card, drawn from a {@link StatusCardModel}. */
public class StatusCardView extends Card
{
    private final TextBlock title = new TextBlock(Type.title(), Palette.TEXT, 2, Sidebar.TEXT_WIDTH);
    private final TextBlock detail = new TextBlock(Type.body(), Palette.TEXT_MUTED, 0, Sidebar.TEXT_WIDTH);
    private final JPanel actions = new JPanel(new Flow(Space.GAP, Space.ROW, Sidebar.TEXT_WIDTH));
    private Consumer<CardAction> onAction = action -> { };
    private Palette palette = Palette.defaults();
    private StatusCardModel model;

    public StatusCardView()
    {
        add(title);
        add(detail);
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(Space.ROW, 0, 0, 0));
        add(actions);
    }

    public void onAction(Consumer<CardAction> handler)
    {
        onAction = handler;
    }

    public void setPalette(Palette palette)
    {
        this.palette = palette;
        if (model != null)
        {
            apply(model);
        }
    }

    public void apply(StatusCardModel model)
    {
        this.model = model;
        setAccent(palette.text(model.getTone()));
        title.setForeground(palette.text(model.getTone()));
        title.setText(model.getTitle());
        detail.setText(model.getDetail());
        detail.setVisible(model.getDetail() != null);
        actions.removeAll();
        if (model.getPrimary() != null)
        {
            actions.add(button(model.getPrimary(), FlatButton.Kind.PRIMARY));
        }
        if (model.getSecondary() != null)
        {
            actions.add(button(model.getSecondary(),
                model.getPrimary() == null ? FlatButton.Kind.SECONDARY : FlatButton.Kind.LINK));
        }
        actions.setVisible(actions.getComponentCount() > 0);
        revalidate();
        repaint();
    }

    public StatusCardModel model()
    {
        return model;
    }

    private FlatButton button(CardAction action, FlatButton.Kind kind)
    {
        FlatButton button = new FlatButton(action.label(), kind);
        button.addActionListener(e -> onAction.accept(action));
        return button;
    }
}
