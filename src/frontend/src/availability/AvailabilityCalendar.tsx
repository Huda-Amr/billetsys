/*
 * Eclipse Public License - v 2.0
 *
 *   THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THE ECLIPSE
 *   PUBLIC LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION
 *   OF THE PROGRAM CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.
 */

import { useMemo, useState } from "react";
import { eachDayOfInterval, format, formatISO } from "date-fns";
import {
  CalendarDaysIcon,
  PencilIcon,
  PlusIcon,
  SearchIcon,
  Trash2Icon,
  XIcon,
} from "lucide-react";
import { toast } from "sonner";

import { AvailabilityBadge } from "@/availability/AvailabilityBadge";
import { AvailabilityDialog } from "@/availability/AvailabilityDialog";
import DataState from "@/components/common/DataState";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import useJson from "@/hooks/useJson";
import useSubmissionGuard from "@/hooks/useSubmissionGuard";
import { cn } from "@/lib/utils";
import { toQueryString } from "@/utils/formatting";
import type { Role } from "@/types/app";
import type { Availability, AvailabilityScope } from "@/types/domain";

interface AvailabilityCalendarProps {
  className?: string;
  role?: Role;
}

interface DialogState {
  open: boolean;
  entry: Availability | null;
  initialDate?: string;
}

interface UserSuggestion {
  id: number;
  name?: string;
  title?: string;
}

interface ViewedUser {
  id: number;
  name: string;
}

function toLocalMidnightDate(isoDate: string): Date {
  const [year, month, day] = isoDate.slice(0, 10).split("-").map(Number);
  return new Date(year, month - 1, day);
}

function availabilityDaysOf(entries: readonly Availability[]): Date[] {
  return entries.flatMap((entry) =>
    eachDayOfInterval({
      start: toLocalMidnightDate(entry.startDate),
      end: toLocalMidnightDate(entry.endDate),
    }),
  );
}

function AvailabilityCalendar({ className, role }: AvailabilityCalendarProps) {
  const guard = useSubmissionGuard();

  const canManageCompany = role === "support" || role === "superuser";
  const canViewOthers = canManageCompany;
  const [scope, setScope] = useState<AvailabilityScope>("PERSONAL");
  const activeScope = canManageCompany ? scope : "PERSONAL";
  const [refreshKey, setRefreshKey] = useState(0);
  const [dialogState, setDialogState] = useState<DialogState>({
    open: false,
    entry: null,
    initialDate: undefined,
  });
  const [viewedUser, setViewedUser] = useState<ViewedUser | null>(null);
  const [userSearch, setUserSearch] = useState("");
  const [userSearchFocused, setUserSearchFocused] = useState(false);
  const [activeUserIndex, setActiveUserIndex] = useState(-1);

  const normalizedUserSearch = userSearch.trim();
  const shouldFetchUserSuggestions =
    canViewOthers && normalizedUserSearch.length >= 2 && viewedUser === null;

  const userSuggestionState = useJson<{ items?: UserSuggestion[] }>(
    shouldFetchUserSuggestions
      ? `/api/users/suggest${toQueryString({ q: normalizedUserSearch })}`
      : null,
  );
  const userSuggestions = userSuggestionState.data?.items ?? [];

  const viewedUserState = useJson<Availability[]>(
    viewedUser
      ? `/api/availability/user/${viewedUser.id}?refresh=${refreshKey}`
      : null,
  );

  const personalState = useJson<Availability[]>(
    `/api/availability/me?refresh=${refreshKey}`,
  );
  const companyState = useJson<Availability[]>(
    `/api/availability/company?refresh=${refreshKey}`,
  );

  const entries = useMemo(() => {
    const data = viewedUser
      ? (viewedUserState.data ?? [])
      : activeScope === "COMPANY"
        ? (companyState.data ?? [])
        : (personalState.data ?? []);

    return [...data].sort((a, b) => a.startDate.localeCompare(b.startDate));
  }, [
    viewedUser,
    viewedUserState.data,
    activeScope,
    personalState.data,
    companyState.data,
  ]);

  const listState = useMemo(
    () => ({
      loading: viewedUser
        ? viewedUserState.loading
        : personalState.loading || companyState.loading,
      error: viewedUser
        ? viewedUserState.error
        : (personalState.error ?? companyState.error),
      unauthorized: viewedUser
        ? viewedUserState.unauthorized
        : personalState.unauthorized || companyState.unauthorized,
      forbidden: viewedUser
        ? viewedUserState.forbidden
        : personalState.forbidden || companyState.forbidden,
      empty: entries.length === 0,
      data: entries,
    }),
    [
      viewedUser,
      viewedUserState.loading,
      viewedUserState.error,
      viewedUserState.unauthorized,
      viewedUserState.forbidden,
      personalState.loading,
      personalState.error,
      personalState.unauthorized,
      personalState.forbidden,
      companyState.loading,
      companyState.error,
      companyState.unauthorized,
      companyState.forbidden,
      entries,
    ],
  );

  const companyBlackoutDays = useMemo(
    () => (viewedUser ? [] : availabilityDaysOf(companyState.data ?? [])),
    [viewedUser, companyState.data],
  );

  const blackoutTimestamps = useMemo(
    () => new Set(companyBlackoutDays.map((d) => d.getTime())),
    [companyBlackoutDays],
  );

  const availabilityDays = useMemo(
    () =>
      availabilityDaysOf(
        viewedUser ? (viewedUserState.data ?? []) : (personalState.data ?? []),
      ).filter((d) => !blackoutTimestamps.has(d.getTime())),
    [viewedUser, viewedUserState.data, personalState.data, blackoutTimestamps],
  );

  const refresh = () => {
    setRefreshKey((current) => current + 1);
  };

  const clearViewedUser = () => {
    setViewedUser(null);
    setUserSearch("");
    setActiveUserIndex(-1);
  };

  const selectViewedUser = (suggestion: UserSuggestion) => {
    setViewedUser({
      id: suggestion.id,
      name: suggestion.name || suggestion.title || `User ${suggestion.id}`,
    });
    setUserSearch("");
    setActiveUserIndex(-1);
  };

  function handleUserSearchKeyDown(
    event: React.KeyboardEvent<HTMLInputElement>,
  ) {
    if (event.key === "Escape") {
      event.preventDefault();
      setUserSearch("");
      setActiveUserIndex(-1);
    } else if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveUserIndex((prev) =>
        prev < userSuggestions.length - 1 ? prev + 1 : 0,
      );
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveUserIndex((prev) =>
        prev > 0 ? prev - 1 : userSuggestions.length - 1,
      );
    } else if (event.key === "Enter") {
      event.preventDefault();
      if (activeUserIndex >= 0 && activeUserIndex < userSuggestions.length) {
        selectViewedUser(userSuggestions[activeUserIndex]);
      }
    }
  }

  const closeDialog = () => {
    setDialogState({ open: false, entry: null, initialDate: undefined });
  };

  const openCreate = (date?: Date) => {
    setDialogState({
      open: true,
      entry: null,
      initialDate: date
        ? formatISO(date, { representation: "date" })
        : undefined,
    });
  };

  const openEdit = (entry: Availability) => {
    setDialogState({ open: true, entry, initialDate: undefined });
  };

  const confirmDelete = async (entry: Availability) => {
    if (!guard.tryEnter()) {
      return;
    }

    try {
      const response = await fetch(`/api/availability/${entry.id}`, {
        method: "DELETE",
        credentials: "same-origin",
        cache: "no-store",
        headers: {
          "X-Billetsys-Client": "react",
        },
      });

      if (response.status === 401) {
        throw new Error("You need to sign in again.");
      }
      if (response.status === 403) {
        throw new Error("You do not have access to this action.");
      }
      if (!response.ok) {
        throw new Error(
          toErrorMessage(response.statusText, "Unable to delete availability."),
        );
      }

      toast.success("Availability deleted.");
      refresh();
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : "Unable to delete availability.",
      );
    } finally {
      guard.exit();
    }
  };

  return (
    <section className={cn("w-full", className)}>
      {canViewOthers ? (
        <div className="relative mb-4 max-w-md">
          <div className="relative">
            <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              type="text"
              value={userSearch}
              onChange={(event) => {
                setUserSearch(event.target.value);
                setActiveUserIndex(-1);
              }}
              onFocus={() => setUserSearchFocused(true)}
              onBlur={() => setUserSearchFocused(false)}
              onKeyDown={handleUserSearchKeyDown}
              placeholder="View another user's availability"
              aria-label="Search users to view their availability"
              className="pl-8"
            />
          </div>
          {userSearchFocused && shouldFetchUserSuggestions && (
            <div className="absolute z-20 mt-1 w-full rounded-md border bg-popover p-1 shadow-md">
              {userSuggestions.length === 0 ? (
                <p className="px-2 py-1.5 text-sm text-muted-foreground">
                  No matching users.
                </p>
              ) : (
                userSuggestions.map((suggestion, index) => (
                  <button
                    key={suggestion.id}
                    type="button"
                    className={cn(
                      "flex w-full flex-col items-start rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent",
                      index === activeUserIndex && "bg-accent",
                    )}
                    onMouseDown={(event) => {
                      // Prevent the input's blur from hiding this option.
                      event.preventDefault();
                      selectViewedUser(suggestion);
                    }}
                  >
                    <span className="truncate font-medium">
                      {suggestion.name || `User ${suggestion.id}`}
                    </span>
                    {suggestion.title ? (
                      <span className="truncate text-xs text-muted-foreground">
                        {suggestion.title}
                      </span>
                    ) : null}
                  </button>
                ))
              )}
            </div>
          )}
        </div>
      ) : null}

      {viewedUser ? (
        <div className="mb-4 flex items-center gap-2">
          <span className="rounded-md bg-muted px-2 py-1 text-sm font-medium">
            Viewing availability of {viewedUser.name}
          </span>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="Stop viewing this user's availability"
            onClick={clearViewedUser}
          >
            <XIcon />
          </Button>
        </div>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        {canManageCompany && !viewedUser ? (
          <Tabs
            value={scope}
            onValueChange={(value) => setScope(value as AvailabilityScope)}
          >
            <TabsList>
              <TabsTrigger value="PERSONAL">Personal</TabsTrigger>
              <TabsTrigger value="COMPANY">Company</TabsTrigger>
            </TabsList>
          </Tabs>
        ) : null}

        {!viewedUser && (
          <Button type="button" onClick={() => openCreate()}>
            <PlusIcon />
            <span>Add availability</span>
          </Button>
        )}
      </div>

      <DataState
        state={listState}
        emptyMessage="No availability periods found."
      >
        {entries.length === 0 ? (
          <p className="mt-6 text-muted-foreground">
            No availability periods found.
          </p>
        ) : (
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <Card>
              <CardContent className="flex justify-center pt-2">
                <Calendar
                  className="w-full min-w-[300px]"
                  modifiers={{
                    unavailable: availabilityDays,
                    blackout: companyBlackoutDays,
                  }}
                  modifiersClassNames={{
                    unavailable:
                      "bg-amber-200 text-black font-semibold ring-1 ring-inset ring-amber-400 hover:bg-amber-300",
                    blackout:
                      "bg-red-200 text-black font-semibold ring-1 ring-inset ring-red-400 hover:bg-red-300",
                  }}
                  onDayClick={(day) => openCreate(day)}
                />
              </CardContent>
            </Card>

            <div className="mt-2 space-y-3">
              {entries.map((entry) => (
                <Card key={entry.id} size="sm">
                  <CardContent className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <CalendarDaysIcon className="size-4 shrink-0 text-muted-foreground" />
                        <span className="truncate font-medium">
                          {format(toLocalMidnightDate(entry.startDate), "PPP")}
                          {" \u2013 "}
                          {format(toLocalMidnightDate(entry.endDate), "PPP")}
                        </span>
                      </div>

                      {entry.reason ? (
                        <p className="mt-1 truncate text-sm text-muted-foreground">
                          {entry.reason}
                        </p>
                      ) : null}
                    </div>

                    <div className="flex shrink-0 items-center gap-1">
                      <AvailabilityBadge scope={entry.scope} />

                      {!viewedUser && (
                        <>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            aria-label="Edit availability"
                            onClick={() => openEdit(entry)}
                          >
                            <PencilIcon />
                          </Button>

                          <AlertDialog>
                            <AlertDialogTrigger asChild>
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label="Delete availability"
                              >
                                <Trash2Icon className="text-destructive" />
                              </Button>
                            </AlertDialogTrigger>

                            <AlertDialogContent>
                              <AlertDialogHeader>
                                <AlertDialogTitle>
                                  Delete availability?
                                </AlertDialogTitle>

                                <AlertDialogDescription>
                                  This action cannot be undone. This will
                                  permanently delete this availability period.
                                </AlertDialogDescription>
                              </AlertDialogHeader>

                              <AlertDialogFooter>
                                <AlertDialogCancel>Cancel</AlertDialogCancel>

                                <AlertDialogAction
                                  variant="destructive"
                                  onClick={() => void confirmDelete(entry)}
                                >
                                  Delete
                                </AlertDialogAction>
                              </AlertDialogFooter>
                            </AlertDialogContent>
                          </AlertDialog>
                        </>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>
        )}
      </DataState>

      <AvailabilityDialog
        open={dialogState.open}
        scope={activeScope}
        entry={dialogState.entry}
        initialDate={dialogState.initialDate}
        canManageCompany={canManageCompany}
        onClose={closeDialog}
        onSaved={refresh}
      />
    </section>
  );
}

function toErrorMessage(text: string, fallback: string): string {
  const trimmed = text.trim();

  if (!trimmed) {
    return fallback;
  }

  if (trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html")) {
    return fallback;
  }

  return trimmed;
}

export { AvailabilityCalendar };
