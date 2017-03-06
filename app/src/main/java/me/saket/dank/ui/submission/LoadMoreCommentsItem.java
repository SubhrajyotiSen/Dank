package me.saket.dank.ui.submission;

import com.google.auto.value.AutoValue;

import net.dean.jraw.models.CommentNode;

/**
 * An item in a submission comments that can load more comments for its parent.
 */
@AutoValue
public abstract class LoadMoreCommentsItem implements SubmissionCommentsRow {

    /**
     * The comment node for which more comments can be fetched.
     */
    public abstract CommentNode parentCommentNode();

    @Override
    public abstract long id();

    @Override
    public Type type() {
        return Type.LOAD_MORE_COMMENTS;
    }

    public static LoadMoreCommentsItem create(CommentNode parentNode) {
        int id = (parentNode.getComment().getId() + "_loadMore").hashCode();
        return new AutoValue_LoadMoreCommentsItem(parentNode, id);
    }

}