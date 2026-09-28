/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/

// Make sure the dom is loaded.
document.addEventListener('DOMContentLoaded', function () {
    // submittable is expected to be text fields.
    var submittables = document.getElementsByClassName('submittable');

    for (var submittable of submittables) {
        // Make text field submit the enclosing form when the enter key is pressed.
        submittable.addEventListener('keydown', function (e) {
            // Check if Enter was pressed without the Shift key
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault(); // Prevent new lines.
                this.form.submit(); // Submit the form.
                console.log(this.form + ' was submitted.');
            }
        });
    }

    // Handle heart and bookmark button clicks without page navigation
    var heartAndBookmarkForms = document.querySelectorAll('form[action*="/heart/"], form[action*="/bookmark/"]');

    for (var form of heartAndBookmarkForms) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();

            var action = this.getAttribute('action');
            var button = this.querySelector('button');

            // Make async request to toggle heart/bookmark (don't follow redirects)
            fetch(action, { redirect: 'follow' })
                .then(response => {
                    // Check if successful (2xx) - ignore 3xx redirects
                    if (response.ok || response.type === 'opaqueredirect') {
                        // Toggle the button icon class
                        if (button.classList.contains('fa')) {
                            button.classList.remove('fa');
                            button.classList.add('far');
                        } else {
                            button.classList.remove('far');
                            button.classList.add('fa');
                        }

                        // Update the form action for next click
                        // Update the form action for next click
                        var isCurrentlySolid = button.classList.contains('fa');
                        var newAction = action.replace(/\/(true|false)$/, isCurrentlySolid ? '/false' : '/true');
                        this.setAttribute('action', newAction); // ← use 'this' instead of 'form'

                        if (action.includes('/heart/')) {
                            var countSpan = this.nextElementSibling; // ← use 'this' instead of 'form'
                            if (countSpan && countSpan.classList.contains('action-count')) {
                                var count = parseInt(countSpan.textContent) || 0;
                                var adding = action.includes('/true');
                                countSpan.textContent = adding ? count + 1 : Math.max(0, count - 1);
                            }
                        }

                    } else {
                        console.error('Failed to update:', response.status);
                    }
                })
                .catch(error => console.error('Error:', error));
        });
    }
});
