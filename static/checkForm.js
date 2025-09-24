let reg = /^(-3|-2|-1|0|[1-5])$/;
let input = document.querySelector('input[name="y"]');
let span = document.querySelector('#errorMessage');
document.querySelector('.btn').onclick = function(e){
    e.preventDefault();
    if(!validate(reg, input.value)){
        notValid(input, span, 'Вы должны ввести цифру от -3 до 5');
    }else{
        valid(input, span, '');
        getResponse(e); // Передаем событие в функцию
    }
}

function validate(regex, inp){
    return regex.test(inp);
}

function notValid(inp, element, message){
    inp.classList.add('is-invalid');
    element.innerHTML = message;
}

function valid(inp, element, message){
    inp.classList.remove('is-invalid');
    inp.classList.add('is-valid');
    element.innerHTML = message;
}

function handleCheckbox(clickedCheckbox) {
    const checkboxes = document.querySelectorAll('input[type="checkbox"][name="hidden-checkbox"]');

    if (clickedCheckbox.checked) {
        checkboxes.forEach(checkbox => {
            if (checkbox !== clickedCheckbox) {
                checkbox.checked = false;
            }
        });
    }
}
async function getResponse(ev){
    ev.preventDefault();

    try {
        const xCheckbox = document.querySelector('input[name="hidden-checkbox"]:checked');
        const yInput = document.querySelector('input[name="y"]');
        const rRadio = document.querySelector('input[name="hidden-radio"]:checked');

        if (!xCheckbox || !yInput || !rRadio) {
            alert('Пожалуйста, заполните все поля');
            return;
        }

        const x = xCheckbox.value;
        const y = yInput.value;
        const r = rRadio.value;

        console.log('Отправляемые данные:', {x, y, r});

        const params = new URLSearchParams({
            r: r,
            x: x,
            y: y
        });

        const response = await fetch(`/fcgi-bin/app.jar?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
            }
        });

        if (!response.ok) {
            throw new Error(`Ошибка HTTP: ${response.status} ${response.statusText}`);
        }

        const answer = await response.text();
        localStorage.setItem("session", answer);
        var res = JSON.parse(answer);

        console.log('Полный ответ от сервера:', res); // Для отладки

        var table = document.getElementById("table");
        var tbody = table.getElementsByTagName("tbody")[0];

        var row = document.createElement("tr");

        // Создаем ячейки
        var xCell = document.createElement("td");
        var yCell = document.createElement("td");
        var rCell = document.createElement("td");
        var timeCell = document.createElement("td");
        var worktimeCell = document.createElement("td");
        var isHitCell = document.createElement("td");

        if (res.error === 'all ok') {
            valid(input, span, '');

            // ✅ ИСПРАВЛЕНИЕ: берем данные из res.current, а не из res
            const currentData = res.current;

            // Заполняем ячейки данными из current
            xCell.innerText = currentData.x || x;
            yCell.innerText = currentData.y || y;
            rCell.innerText = currentData.r || r;
            timeCell.innerText = currentData.timestamp || new Date().toLocaleString();
            worktimeCell.innerText = res.workTime ? res.workTime + ' мс' : '0 мс';

            // Используем hit из currentData
            if (currentData.hit === true) {
                isHitCell.innerText = "✓";
                isHitCell.style.color = "green";
                isHitCell.style.fontWeight = "bold";
            } else {
                isHitCell.innerText = "✗";
                isHitCell.style.color = "red";
                isHitCell.style.fontWeight = "bold";
            }

            // Правильный порядок ячеек
            row.appendChild(xCell);      // X
            row.appendChild(yCell);      // Y
            row.appendChild(rCell);      // R
            row.appendChild(timeCell);   // Время
            row.appendChild(worktimeCell); // Время работы
            row.appendChild(isHitCell);  // Результат
            tbody.appendChild(row);
            updateDotPosition(currentData.x || x, currentData.y || y, currentData.r || r);

        } else {
            alert('Ошибка сервера: ' + (res.error || 'Неизвестная ошибка'));
        }

    } catch (error) {
        console.error('Ошибка запроса:', error);
        alert(`Ошибка: ${error.message}`);
    }
}

function updateDotPosition(x, y, r) {
    const dot = document.getElementById("dot");
    if (!dot) return;

    const svgX = 150 + (parseFloat(x) / parseFloat(r)) * 75;
    const svgY = 150 - (parseFloat(y) / parseFloat(r)) * 75;

    dot.setAttribute("cx", svgX);
    dot.setAttribute("cy", svgY);
    dot.style.display = 'block';
}