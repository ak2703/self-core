/**
 * Copyright (c) 2020, Self XDSD Contributors
 * All rights reserved.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"),
 * to read the Software only. Permission is hereby NOT GRANTED to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.selfxdsd.core.contributors;

import com.selfxdsd.api.*;
import com.selfxdsd.api.storage.Storage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contributors of a Project. This class <b>just represents</b>
 * the contributors. The actual filtering has to be done in an upper layer.
 * @author Mihai Andronache (amihaiemil@gmail.com)
 * @version $Id$
 * @since 0.0.4
 */
public final class ProjectContributors implements Contributors {

    /**
     * The Project.
     */
    private final Project project;

    /**
     * Full name of the Repo represented by the Project.
     */
    private final String repoFullName;

    /**
     * Provider of the Repo represented by the Project.
     */
    private final String provider;

    /**
     * The project's contributors.
     */
    private final Supplier<Stream<Contributor>> contributors;

    /**
     * Self storage, to save new contributors.
     */
    private final Storage storage;

    /**
     * Constructor.
     * @param project The project.
     * @param contributors Project's contributors.
     * @param storage Self's storage, to save new contracts.
     */
    public ProjectContributors(
        final Project project,
        final Supplier<Stream<Contributor>> contributors,
        final Storage storage
    ) {
        this.project = project;
        this.repoFullName = project.repoFullName();
        this.provider = project.owner().provider().name();
        this.contributors = contributors;
        this.storage = storage;
    }

    /**
     * Register a new Contributor to this Project.
     * By default, a DEV Contract with hourly rate 0 will be
     * created.
     * @param username Username.
     * @param provider Password.
     * @return Contributor.
     */
    @Override
    public Contributor register(
        final String username,
        final String provider
    ) {
        if(!provider.equals(this.provider)) {
            throw new IllegalArgumentException(
                "You can only register contributors working at "
              + this.provider + " here."
            );
        }
        Contributor found = this.getById(
            username, provider
        );
        if(found == null) {
            found = this.storage.contributors().register(username, provider);
            this.storage.contracts().addContract(
                this.repoFullName, username, this.provider,
                BigDecimal.valueOf(0), Contract.Roles.DEV
            );
        }
        return found;
    }

    @Override
    public Contributor getById(
        final String username,
        final String provider
    ) {
        return this.contributors.get()
            .filter(c -> c.username().equals(username)
                && c.provider().equals(provider))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Contributors ofProject(
        final String repoFullName,
        final String repoProvider
    ) {
        if(this.repoFullName.equals(repoFullName)
            && this.provider.equals(repoProvider)) {
            return this;
        }
        throw new IllegalStateException(
            "Already seeing the contributors of Project " + this.repoFullName
          + ", operating at " + this.provider + "."
        );
    }

    /**
     * Elect a contributor for the given Task.
     * At the moment we will elect a random Contributor out
     * of those who have the necessary role.
     *
     * In the future, we might take more factors into account.
     * @param task Task requiring an assignee.
     * @return Contributor or null if nobody is found.
     * @checkstyle ReturnCount (30 lines)
     * @checkstyle Indentation (30 lines)
     */
    @Override
    public Contributor elect(final Task task) {
        final List<Contributor> eligible = this.contributors.get()
            .filter(
                contributor -> {
                    if(task.assignee() != null) {
                        return !contributor.username().equals(
                            task.assignee().username()
                        );
                    }
                    return true;
                }
            ).filter(
                contributor -> {
                    for(final Contract contract : contributor.contracts()) {
                        if(contract.role().equals(task.role())) {
                            final BigDecimal price = contract.hourlyRate()
                                .multiply(
                                    BigDecimal.valueOf(task.estimation())
                                ).divide(
                                    BigDecimal.valueOf(60),
                                    RoundingMode.HALF_UP
                                ).add(task
                                    .project()
                                    .projectManager()
                                    .commission()
                                );
                            final BigDecimal budget = this.project
                                .wallet()
                                .available();
                            return price.compareTo(budget) <= 0;
                        }
                    }
                    return false;
                }
            ).collect(Collectors.toList());
        if(eligible.size() > 0) {
            Collections.shuffle(eligible);
            return eligible.get(0);
        }
        return null;
    }

    @Override
    public Iterator<Contributor> iterator() {
        return this.contributors.get().iterator();
    }
}
