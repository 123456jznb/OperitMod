import { useEffect, useRef, useState } from 'react';
import { ChevronDownIcon } from '../util/chatIcons';

export function ScrollToBottomButton({
  scrollElement,
  autoScrollToBottom,
  hasNewerDisplayHistory = false,
  onRequestLatestMessages,
  onAutoScrollToBottomChange
}: {
  scrollElement: HTMLDivElement | null;
  autoScrollToBottom: boolean;
  hasNewerDisplayHistory?: boolean;
  onRequestLatestMessages?: (() => void) | null;
  onAutoScrollToBottomChange: (value: boolean) => void;
}) {
  const [showScrollButton, setShowScrollButton] = useState(false);
  const autoScrollToBottomRef = useRef(autoScrollToBottom);
  const hasNewerDisplayHistoryRef = useRef(hasNewerDisplayHistory);
  const userInteractionActiveRef = useRef(false);

  useEffect(() => {
    autoScrollToBottomRef.current = autoScrollToBottom;
    if (autoScrollToBottom) {
      setShowScrollButton(false);
    }
  }, [autoScrollToBottom]);

  useEffect(() => {
    hasNewerDisplayHistoryRef.current = hasNewerDisplayHistory;
  }, [hasNewerDisplayHistory]);

  useEffect(() => {
    if (!scrollElement) {
      return;
    }

    let interactionTimeoutId = 0;
    let lastPosition = scrollElement.scrollTop;

    const setTransientInteractionActive = () => {
      userInteractionActiveRef.current = true;
      window.clearTimeout(interactionTimeoutId);
      interactionTimeoutId = window.setTimeout(() => {
        userInteractionActiveRef.current = false;
      }, 180);
    };
    const beginPersistentInteraction = () => {
      userInteractionActiveRef.current = true;
      window.clearTimeout(interactionTimeoutId);
    };
    const endPersistentInteraction = () => {
      userInteractionActiveRef.current = false;
      window.clearTimeout(interactionTimeoutId);
    };
    const handleScroll = () => {
      const currentPosition = scrollElement.scrollTop;
      const movedAwayFromBottom = currentPosition < lastPosition;

      if (movedAwayFromBottom) {
        if (autoScrollToBottomRef.current && userInteractionActiveRef.current) {
          onAutoScrollToBottomChange(false);
          setShowScrollButton(true);
        }
      } else {
        const isAtBottom =
          Math.abs(
            scrollElement.scrollHeight - scrollElement.clientHeight - scrollElement.scrollTop
          ) <= 1 && !hasNewerDisplayHistoryRef.current;
        if (isAtBottom && !autoScrollToBottomRef.current) {
          onAutoScrollToBottomChange(true);
          setShowScrollButton(false);
        }
      }

      lastPosition = currentPosition;
    };

    scrollElement.addEventListener('scroll', handleScroll, { passive: true });
    scrollElement.addEventListener('wheel', setTransientInteractionActive, { passive: true });
    scrollElement.addEventListener('pointerdown', beginPersistentInteraction, { passive: true });
    scrollElement.addEventListener('pointerup', endPersistentInteraction, { passive: true });
    scrollElement.addEventListener('pointercancel', endPersistentInteraction, { passive: true });
    scrollElement.addEventListener('touchstart', beginPersistentInteraction, { passive: true });
    scrollElement.addEventListener('touchend', endPersistentInteraction, { passive: true });
    scrollElement.addEventListener('touchcancel', endPersistentInteraction, { passive: true });

    return () => {
      window.clearTimeout(interactionTimeoutId);
      userInteractionActiveRef.current = false;
      scrollElement.removeEventListener('scroll', handleScroll);
      scrollElement.removeEventListener('wheel', setTransientInteractionActive);
      scrollElement.removeEventListener('pointerdown', beginPersistentInteraction);
      scrollElement.removeEventListener('pointerup', endPersistentInteraction);
      scrollElement.removeEventListener('pointercancel', endPersistentInteraction);
      scrollElement.removeEventListener('touchstart', beginPersistentInteraction);
      scrollElement.removeEventListener('touchend', endPersistentInteraction);
      scrollElement.removeEventListener('touchcancel', endPersistentInteraction);
    };
  }, [onAutoScrollToBottomChange, scrollElement]);

  if (!showScrollButton) {
    return null;
  }

  return (
    <button
      className="scroll-to-bottom-button"
      onClick={() => {
        if (hasNewerDisplayHistory) {
          onRequestLatestMessages?.();
        }
        scrollElement?.scrollTo({
          top: scrollElement.scrollHeight,
          behavior: 'smooth'
        });
        onAutoScrollToBottomChange(true);
        setShowScrollButton(false);
      }}
      type="button"
    >
      <ChevronDownIcon size={18} />
    </button>
  );
}
